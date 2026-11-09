package kr.ktb.zura.needu.aichat.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import kr.ktb.zura.needu.aichat.config.AiConversationLockProperties;
import kr.ktb.zura.needu.aichat.config.AiReplyQueueProperties;
import kr.ktb.zura.needu.aichat.entity.AiMessage;
import kr.ktb.zura.needu.aichat.entity.AiReplyJob;
import kr.ktb.zura.needu.aichat.repository.AiChatRoomRepository;
import kr.ktb.zura.needu.aichat.repository.AiReplyJobRepository;
import kr.ktb.zura.needu.aichat.type.AiReplyJobStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class AiReplyJobService {

    static final String ABANDONED_ERROR_CODE = "AICHAT_REPLY_ABANDONED";
    static final String MAX_DELIVERIES_EXCEEDED_ERROR_CODE = "AICHAT_REPLY_MAX_DELIVERIES_EXCEEDED";
    private static final List<AiReplyJobStatus> UNFINISHED_STATUSES =
            List.of(AiReplyJobStatus.QUEUED, AiReplyJobStatus.RUNNING);
    private static final Limit RECONCILE_BATCH_LIMIT = Limit.of(100);

    private final AiReplyJobRepository aiReplyJobRepository;
    private final AiChatRoomRepository aiChatRoomRepository;
    private final AiMessageService aiMessageService;
    // 지금 경로는 AI 서버에 요청한 뒤 답변을 받을 때까지 작업 행을 갱신하지 않음 => DB만 봐서는 처리 중인지 서버가 죽었는지 확인 불가
    // 정상 처리는 대화방 락 시간(120초) 안에 끝남 => 그보다 오래 갱신이 없는 미완료 작업은 죽은 것으로 판단
    // 큐 경로 작업은 대기열에서 120초 넘게 기다릴 수 있고, 재투입 스케줄러와 할당 횟수 제한이 끝까지 처리하므로 이 기준을 쓰지 않는다
    private final Duration abandonedAfter;
    private final boolean replyQueueEnabled;
    private final int maxDeliveries;
    private final Clock clock;

    public AiReplyJobService(AiReplyJobRepository aiReplyJobRepository,
                             AiChatRoomRepository aiChatRoomRepository,
                             AiMessageService aiMessageService,
                             AiConversationLockProperties lockProperties,
                             AiReplyQueueProperties queueProperties,
                             Clock clock) {
        this.aiReplyJobRepository = aiReplyJobRepository;
        this.aiChatRoomRepository = aiChatRoomRepository;
        this.aiMessageService = aiMessageService;
        this.abandonedAfter = lockProperties.ttl();
        this.replyQueueEnabled = queueProperties.enabled();
        this.maxDeliveries = queueProperties.maxDeliveries();
        this.clock = clock;
    }

    @Transactional
    public AiReplyJobDispatch createUserMessageAndJob(Long conversationId, Long userId, UUID clientMessageId,
                                                      String content, String lockToken) {
        AiMessage userMessage = aiMessageService.createUserMessage(conversationId, clientMessageId, content);
        AiReplyJob job = aiReplyJobRepository.save(AiReplyJob.create(
                aiChatRoomRepository.getReferenceById(conversationId), userMessage.getId(), userId, lockToken));
        return AiReplyJobDispatch.of(job, userMessage);
    }

    @Transactional(readOnly = true)
    public boolean isReplyInProgress(Long userMessageId) {
        return aiReplyJobRepository.findByUserMessageId(userMessageId)
                .filter(job -> !job.isFinished() && !isAbandoned(job))
                .isPresent();
    }

    @Transactional(readOnly = true)
    public boolean existsReplyInProgress(Long conversationId) {
        return aiReplyJobRepository.findAllByAiChatRoomIdAndStatusIn(conversationId, UNFINISHED_STATUSES).stream()
                .anyMatch(job -> !isAbandoned(job));
    }

    @Transactional
    public AiReplyJobDispatch restartJob(Long conversationId, AiMessage userMessage, Long userId, String lockToken) {
        Optional<AiReplyJob> found = aiReplyJobRepository.findByUserMessageId(userMessage.getId());
        if (found.isEmpty()) {
            AiReplyJob job = aiReplyJobRepository.save(AiReplyJob.create(
                    aiChatRoomRepository.getReferenceById(conversationId), userMessage.getId(), userId, lockToken));
            return AiReplyJobDispatch.of(job, userMessage);
        }

        AiReplyJob job = found.get();
        if (!job.isFinished()) {
            job.fail(ABANDONED_ERROR_CODE, now());
        }
        job.restart(lockToken);
        return AiReplyJobDispatch.of(job, userMessage);
    }

    @Transactional
    public void recordStreamEntry(Long jobId, String dispatchToken, String streamEntryId) {
        aiReplyJobRepository.updateStreamEntryId(jobId, dispatchToken, streamEntryId);
    }

    @Transactional(readOnly = true)
    public List<AiReplyJob> findAllStaleJobs(Duration staleAfter) {
        return aiReplyJobRepository.findAllByStatusInAndUpdatedAtBeforeOrderByIdAsc(
                UNFINISHED_STATUSES, now().minus(staleAfter), RECONCILE_BATCH_LIMIT);
    }

    // 조회한 뒤 워커가 가져가거나 끝냈으면 그 처리를 그대로 둔다
    @Transactional
    public Optional<String> redispatch(Long jobId, String dispatchToken, AiReplyJobStatus status) {
        Optional<AiReplyJob> found = aiReplyJobRepository.findByIdForUpdate(jobId);
        if (found.isEmpty() || !found.get().isDispatchedWith(dispatchToken) || found.get().getStatus() != status) {
            return Optional.empty();
        }
        AiReplyJob job = found.get();
        job.redispatch();
        log.warn("AI reply job redispatched. jobId={}, attempt={}, conversationId={}, userMessageId={}, previousStatus={}",
                jobId, job.getAttempt(), job.getAiChatRoom().getId(), job.getUserMessageId(), status);
        return Optional.of(job.getDispatchToken());
    }

    @Transactional(readOnly = true)
    public boolean isOwnedBy(Long jobId, int attempt) {
        return aiReplyJobRepository.findById(jobId)
                .filter(job -> job.isOwnedBy(attempt))
                .isPresent();
    }

    @Transactional
    public AiReplyJobTakeResult take(Long jobId, String dispatchToken, long deliveryCount, String streamEntryId) {
        Optional<AiReplyJob> found = aiReplyJobRepository.findByIdForUpdate(jobId);
        if (found.isEmpty()
                || !found.get().isDispatchedWith(dispatchToken)
                || found.get().getStatus() == AiReplyJobStatus.FAILED) {
            return AiReplyJobTakeResult.stale(jobId);
        }
        AiReplyJob job = found.get();
        if (job.getStatus() == AiReplyJobStatus.SUCCEEDED) {
            return AiReplyJobTakeResult.alreadySucceeded(job);
        }
        if (deliveryCount > maxDeliveries) {
            job.fail(MAX_DELIVERIES_EXCEEDED_ERROR_CODE, now());
            log.error("AI reply job gave up. jobId={}, attempt={}, conversationId={}, userMessageId={}, deliveryCount={}",
                    jobId, job.getAttempt(), job.getAiChatRoom().getId(), job.getUserMessageId(), deliveryCount);
            return AiReplyJobTakeResult.gaveUp(job);
        }
        job.take(streamEntryId);
        log.info("AI reply job taken. jobId={}, attempt={}, conversationId={}, userMessageId={}",
                jobId, job.getAttempt(), job.getAiChatRoom().getId(), job.getUserMessageId());
        return AiReplyJobTakeResult.taken(job, aiMessageService.findMessage(job.getUserMessageId()));
    }

    @Transactional
    public AiReplyCommitResult commitReply(Long jobId, int attempt, String content, int progress,
                                           boolean inputLocked, LocalDateTime purgeAt) {
        AiReplyJob job = findJobForUpdate(jobId);
        if (!job.isOwnedBy(attempt)) {
            logRejected("AI reply commit rejected.", job, attempt);
            return AiReplyCommitResult.rejected();
        }
        AiMessage reply = aiMessageService.createReplyAndUpdateRoom(
                job.getAiChatRoom().getId(), job.getUserMessageId(), content, progress, inputLocked, purgeAt);
        job.succeed(now());
        return AiReplyCommitResult.saved(reply);
    }

    @Transactional
    public boolean failTakenJob(Long jobId, int attempt, String errorCode) {
        AiReplyJob job = findJobForUpdate(jobId);
        if (!job.isOwnedBy(attempt)) {
            logRejected("AI reply failure record rejected.", job, attempt);
            return false;
        }
        job.fail(errorCode, now());
        return true;
    }

    @Transactional
    public void failJob(Long userMessageId, String errorCode) {
        AiReplyJob job = findJob(userMessageId);
        if (!job.isFinished()) {
            job.fail(errorCode, now());
        }
    }

    private AiReplyJob findJobForUpdate(Long jobId) {
        return aiReplyJobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new IllegalStateException("AI reply job not found. jobId=" + jobId));
    }

    private void logRejected(String message, AiReplyJob job, int attempt) {
        log.info(message + " jobId={}, attempt={}, currentAttempt={}, status={}, conversationId={}, userMessageId={}",
                job.getId(), attempt, job.getAttempt(), job.getStatus(), job.getAiChatRoom().getId(),
                job.getUserMessageId());
    }

    private AiReplyJob findJob(Long userMessageId) {
        return aiReplyJobRepository.findByUserMessageId(userMessageId)
                .orElseThrow(() -> new IllegalStateException(
                        "AI reply job not found. userMessageId=" + userMessageId));
    }

    // 플래그를 켜는 동안 받은 서버가 바로 처리하던 작업이 남아 있을 수 있어 그 작업에는 시간 기준을 그대로 쓴다
    private boolean isAbandoned(AiReplyJob job) {
        if (replyQueueEnabled && !job.isRunningWithoutStreamEntry()) {
            return false;
        }
        return !job.getUpdatedAt().isAfter(abandonedSince());
    }

    private LocalDateTime abandonedSince() {
        return now().minus(abandonedAfter);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
