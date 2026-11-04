package kr.ktb.zura.needu.aichat.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import kr.ktb.zura.needu.aichat.config.AiConversationLockProperties;
import kr.ktb.zura.needu.aichat.entity.AiMessage;
import kr.ktb.zura.needu.aichat.entity.AiReplyJob;
import kr.ktb.zura.needu.aichat.repository.AiChatRoomRepository;
import kr.ktb.zura.needu.aichat.repository.AiReplyJobRepository;
import kr.ktb.zura.needu.aichat.type.AiReplyJobStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiReplyJobService {

    static final String ABANDONED_ERROR_CODE = "AICHAT_REPLY_ABANDONED";
    private static final List<AiReplyJobStatus> UNFINISHED_STATUSES =
            List.of(AiReplyJobStatus.QUEUED, AiReplyJobStatus.RUNNING);

    private final AiReplyJobRepository aiReplyJobRepository;
    private final AiChatRoomRepository aiChatRoomRepository;
    private final AiMessageService aiMessageService;
    // 지금 경로는 AI 서버에 요청한 뒤 답변을 받을 때까지 작업 행을 갱신하지 않음 => DB만 봐서는 처리 중인지 서버가 죽었는지 확인 불가
    // 정상 처리는 대화방 락 시간(120초) 안에 끝남 => 그보다 오래 갱신이 없는 미완료 작업은 죽은 것으로 판단
    // 큐 경로는 하트비트와 재배달로 죽은 작업을 찾을 수 있기에 이 기준을 쓰지 않는다 (큐 도입 이후 옛 경로 제거와 함께 삭제)
    private final Duration abandonedAfter;
    private final Clock clock;

    public AiReplyJobService(AiReplyJobRepository aiReplyJobRepository,
                             AiChatRoomRepository aiChatRoomRepository,
                             AiMessageService aiMessageService,
                             AiConversationLockProperties lockProperties,
                             Clock clock) {
        this.aiReplyJobRepository = aiReplyJobRepository;
        this.aiChatRoomRepository = aiChatRoomRepository;
        this.aiMessageService = aiMessageService;
        this.abandonedAfter = lockProperties.ttl();
        this.clock = clock;
    }

    @Transactional
    public AiMessage createUserMessageAndJob(Long conversationId, Long userId, UUID clientMessageId,
                                             String content, String lockToken) {
        AiMessage userMessage = aiMessageService.createUserMessage(conversationId, clientMessageId, content);
        aiReplyJobRepository.save(AiReplyJob.create(
                aiChatRoomRepository.getReferenceById(conversationId), userMessage.getId(), userId, lockToken));
        return userMessage;
    }

    @Transactional(readOnly = true)
    public boolean isReplyInProgress(Long userMessageId) {
        return aiReplyJobRepository.findByUserMessageId(userMessageId)
                .filter(job -> !job.isFinished() && !isAbandoned(job))
                .isPresent();
    }

    @Transactional(readOnly = true)
    public boolean existsReplyInProgress(Long conversationId) {
        return aiReplyJobRepository.existsByAiChatRoomIdAndStatusInAndUpdatedAtAfter(
                conversationId, UNFINISHED_STATUSES, abandonedSince());
    }

    @Transactional
    public void restartJob(Long conversationId, Long userMessageId, Long userId, String lockToken) {
        Optional<AiReplyJob> found = aiReplyJobRepository.findByUserMessageId(userMessageId);
        if (found.isEmpty()) {
            aiReplyJobRepository.save(AiReplyJob.create(
                    aiChatRoomRepository.getReferenceById(conversationId), userMessageId, userId, lockToken));
            return;
        }

        AiReplyJob job = found.get();
        if (!job.isFinished()) {
            job.fail(ABANDONED_ERROR_CODE, now());
        }
        job.restart(lockToken);
    }

    @Transactional
    public void startJob(Long userMessageId) {
        findJob(userMessageId).start();
    }

    @Transactional
    public AiMessage createReplyAndSucceed(Long conversationId, Long userMessageId, String content,
                                           int progress, boolean inputLocked, LocalDateTime purgeAt) {
        AiMessage reply = aiMessageService.createReplyAndUpdateRoom(
                conversationId, userMessageId, content, progress, inputLocked, purgeAt);
        findJob(userMessageId).succeed(now());
        return reply;
    }

    @Transactional
    public void failJob(Long userMessageId, String errorCode) {
        AiReplyJob job = findJob(userMessageId);
        if (!job.isFinished()) {
            job.fail(errorCode, now());
        }
    }

    private AiReplyJob findJob(Long userMessageId) {
        return aiReplyJobRepository.findByUserMessageId(userMessageId)
                .orElseThrow(() -> new IllegalStateException(
                        "AI reply job not found. userMessageId=" + userMessageId));
    }

    private boolean isAbandoned(AiReplyJob job) {
        return !job.getUpdatedAt().isAfter(abandonedSince());
    }

    private LocalDateTime abandonedSince() {
        return now().minus(abandonedAfter);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
