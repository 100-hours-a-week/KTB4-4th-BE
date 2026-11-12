package kr.ktb.zura.needu.aichat.facade;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import io.micrometer.core.instrument.Timer;
import kr.ktb.zura.needu.aichat.client.AiChatClient;
import kr.ktb.zura.needu.aichat.client.dto.request.AiServerAnalysisKeywordsRequest;
import kr.ktb.zura.needu.aichat.client.dto.request.AiServerFeedbackRequest;
import kr.ktb.zura.needu.aichat.client.dto.request.AiServerPatchAnalysisRequest;
import kr.ktb.zura.needu.aichat.client.dto.request.AiServerUserProfileRequest;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerCloseSessionResponse;
import kr.ktb.zura.needu.aichat.config.AiReplyQueueProperties;
import kr.ktb.zura.needu.aichat.dto.request.PatchAnalyzeMessageRequest;
import kr.ktb.zura.needu.aichat.dto.request.SendMessageRequest;
import kr.ktb.zura.needu.aichat.dto.response.AiConversationResponse;
import kr.ktb.zura.needu.aichat.dto.response.AiMessageAcceptedResponse;
import kr.ktb.zura.needu.aichat.dto.response.AiMessageResponse;
import kr.ktb.zura.needu.aichat.dto.response.AiMessageSummaryResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerAnalysisResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerAnalysisKeywordResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerSendMessageResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerStartSessionResponse;
import kr.ktb.zura.needu.aichat.dto.response.AnalysisResultResponse;
import kr.ktb.zura.needu.aichat.dto.response.ProductRecommendationStatusResponse;
import kr.ktb.zura.needu.aichat.dto.response.RestartRequiredResponse;
import kr.ktb.zura.needu.aichat.entity.AiChatRoom;
import kr.ktb.zura.needu.aichat.entity.AiMessage;
import kr.ktb.zura.needu.aichat.exception.AiChatErrorCode;
import kr.ktb.zura.needu.aichat.service.AiAnalysisConfirmService;
import kr.ktb.zura.needu.aichat.service.AiChatEventPublisher;
import kr.ktb.zura.needu.aichat.service.AiChatRoomService;
import kr.ktb.zura.needu.aichat.service.AiChatStreamService;
import kr.ktb.zura.needu.aichat.service.AiChatTurnEvents;
import kr.ktb.zura.needu.aichat.service.AiConversationLock;
import kr.ktb.zura.needu.aichat.service.AiGenerationStateService;
import kr.ktb.zura.needu.aichat.service.AiMessageService;
import kr.ktb.zura.needu.aichat.service.AiReplyCommitResult;
import kr.ktb.zura.needu.aichat.service.AiReplyJobDispatch;
import kr.ktb.zura.needu.aichat.service.AiReplyJobService;
import kr.ktb.zura.needu.aichat.service.AiReplyJobTakeResult;
import kr.ktb.zura.needu.aichat.service.AiReplyQueueMetrics;
import kr.ktb.zura.needu.aichat.service.AiReplyQueueService;
import kr.ktb.zura.needu.aichat.service.AiReplyTaskExecutor;
import kr.ktb.zura.needu.aichat.sse.AiChatCompletedEvent;
import kr.ktb.zura.needu.aichat.type.AiReplyProcessingResult;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.common.exception.ErrorCode;
import kr.ktb.zura.needu.common.exception.TooManyRequestsException;
import kr.ktb.zura.needu.common.response.CursorPageResponse;
import kr.ktb.zura.needu.product.dto.response.ProductActivityResponse;
import kr.ktb.zura.needu.product.service.ProductActivityService;
import kr.ktb.zura.needu.user.service.ConsentService;
import kr.ktb.zura.needu.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

// AI 서버 호출과 DB 저장을 서로 다른 트랜잭션으로 나눠야 함 => 이 클래스는 트랜잭션을 시작하지 않음
@Slf4j
@Component
public class AiChatFacade {

    private static final long FIRST_DELIVERY = 1L;
    private static final String LOCK_LOST_ERROR_CODE = "AICHAT_REPLY_LOCK_LOST";

    private final AiChatRoomService aiChatRoomService;
    private final AiMessageService aiMessageService;
    private final AiReplyJobService aiReplyJobService;
    private final AiConversationLock aiConversationLock;
    private final AiGenerationStateService aiGenerationStateService;
    private final AiChatClient aiChatClient;
    private final AiAnalysisConfirmService aiAnalysisConfirmService;
    private final AiChatStreamService aiChatStreamService;
    private final AiChatEventPublisher aiChatEventPublisher;
    private final AiReplyTaskExecutor aiReplyTaskExecutor;
    private final AiReplyQueueService aiReplyQueueService;
    private final AiReplyQueueMetrics aiReplyQueueMetrics;
    private final AiReplyQueueProperties replyQueueProperties;
    private final UserService userService;
    private final ConsentService consentService;
    private final ProductActivityService productActivityService;
    private final Clock clock;
    private final Duration turnTimeout;
    // 하트비트를 두 번 넘게 놓치면 곧 회수될 수 있다 => 이보다 오래 확인되지 않았으면 AI를 부르지 않는다
    private final Duration maxLeaseSilence;
    private final Duration messageRetryAfter;
    private final Duration shortCompletionCooldown;
    private final Set<Long> shortCooldownExternalIds;

    public AiChatFacade(AiChatRoomService aiChatRoomService,
                        AiMessageService aiMessageService,
                        AiReplyJobService aiReplyJobService,
                        AiConversationLock aiConversationLock,
                        AiGenerationStateService aiGenerationStateService,
                        AiChatClient aiChatClient,
                        AiAnalysisConfirmService aiAnalysisConfirmService,
                        AiChatStreamService aiChatStreamService,
                        AiChatEventPublisher aiChatEventPublisher,
                        AiReplyTaskExecutor aiReplyTaskExecutor,
                        AiReplyQueueService aiReplyQueueService,
                        AiReplyQueueMetrics aiReplyQueueMetrics,
                        AiReplyQueueProperties replyQueueProperties,
                        UserService userService,
                        ConsentService consentService,
                        ProductActivityService productActivityService,
                        Clock clock,
                        @Value("${ai.server.message-read-timeout}") Duration turnTimeout,
                        @Value("${ai.chat.message-retry-after:10s}") Duration messageRetryAfter,
                        @Value("${ai.chat.short-completion-cooldown:5m}") Duration shortCompletionCooldown,
                        @Value("${ai.chat.short-cooldown-external-ids:}") Set<Long> shortCooldownExternalIds) {
        this.aiChatRoomService = aiChatRoomService;
        this.aiMessageService = aiMessageService;
        this.aiReplyJobService = aiReplyJobService;
        this.aiConversationLock = aiConversationLock;
        this.aiGenerationStateService = aiGenerationStateService;
        this.aiChatClient = aiChatClient;
        this.aiAnalysisConfirmService = aiAnalysisConfirmService;
        this.aiChatStreamService = aiChatStreamService;
        this.aiChatEventPublisher = aiChatEventPublisher;
        this.aiReplyTaskExecutor = aiReplyTaskExecutor;
        this.aiReplyQueueService = aiReplyQueueService;
        this.aiReplyQueueMetrics = aiReplyQueueMetrics;
        this.replyQueueProperties = replyQueueProperties;
        this.userService = userService;
        this.productActivityService = productActivityService;
        this.consentService = consentService;
        this.clock = clock;
        this.turnTimeout = turnTimeout;
        this.maxLeaseSilence = replyQueueProperties.claimMinIdle()
                .minus(replyQueueProperties.heartbeatInterval().multipliedBy(2));
        this.messageRetryAfter = messageRetryAfter;
        this.shortCompletionCooldown = shortCompletionCooldown;
        this.shortCooldownExternalIds = shortCooldownExternalIds;
    }

    public AiConversationStartResult startOrResumeConversation(Long userId) {
        userService.validateActiveUser(userId);
        validateAiConversationConsent(userId);
        boolean hasShortCooldown = hasShortCompletionCooldown(userId);
        AiChatRoom room = hasShortCooldown
                ? aiChatRoomService.findOrReserveRoomWithoutDailyLimit(userId, shortCompletionCooldown)
                : aiChatRoomService.findOrReserveRoom(userId);
        if (room.isPending()) {
            return startConversation(userId, room.getId());
        }
        // AI 서버에 세션 조회 API가 없어 purgeAt으로 만료를 판단, 규칙보다 일찍 사라진 세션은 메시지 전송·분석 시 발견
        // ANALYZING도 AI 세션이 만료되면 분석을 이어갈 수 없음 => 다른 대화 API처럼 만료로 보고 새 대화를 시작한다
        if (!room.isCompleted() && room.isExpiredAt(LocalDateTime.now(ZoneOffset.UTC))) {
            return restartConversation(userId, room.getId(), hasShortCooldown);
        }
        return AiConversationStartResult.resumed(
                AiConversationResponse.from(
                        room,
                        aiMessageService.findLatestProgress(room.getId()),
                        hasShortCooldown
                                ? aiChatRoomService.findNextConversationAvailableAt(room, shortCompletionCooldown)
                                : aiChatRoomService.findNextConversationAvailableAt(room)));
    }



    public CursorPageResponse<AiMessageSummaryResponse> findAllMessages(
            Long userId, Long conversationId, String cursor, int size) {
        userService.validateActiveUser(userId);
        boolean hasShortCooldown = hasShortCompletionCooldown(userId);

        if (hasShortCooldown) {
            aiChatRoomService.validateReadableRoom(
                    userId, conversationId, shortCompletionCooldown);
        } else {
            aiChatRoomService.validateReadableRoom(userId, conversationId);
        }

        return aiMessageService.findAllMessages(conversationId, cursor, size);
    }

    // 연결 전 오류(403, 404, 409 입력 잠김, 410 만료)는 HTTP 오류로, 연결 후 오류는 error 이벤트로 보낸다
    public SseEmitter connectStream(Long userId, Long conversationId) {
        userService.validateActiveUser(userId);
        AiChatRoom room = aiChatRoomService.findStreamableRoom(userId, conversationId);
        return aiChatStreamService.connect(room);
    }

    public AiMessageSendResult sendMessage(Long userId, Long conversationId, SendMessageRequest request) {
        userService.validateActiveUser(userId);
        validateAiConversationConsent(userId);
        aiChatRoomService.validateMessageSendableRoom(userId, conversationId);
        Optional<AiMessageSendResult> resentResult = findResentResult(conversationId, request);
        if (resentResult.isPresent()) {
            return resentResult.get();
        }
        Optional<AiConversationLock.Handle> lock = aiConversationLock.tryLock(conversationId);
        if (lock.isEmpty()) {
            log.info("AI message already in progress. userId={}, conversationId={}", userId, conversationId);
            throw new TooManyRequestsException(messageRetryAfter.toSeconds());
        }
        try {
            return acceptMessage(userId, conversationId, request, lock.get());
        } catch (RuntimeException e) {
            aiConversationLock.unlock(lock.get());
            throw e;
        }
    }

    private void validateAiConversationConsent(Long userId) {
        if (!consentService.hasAgreedToAiConversation(userId)) {
            throw new BusinessException(AiChatErrorCode.AICHAT_CONSENT_REQUIRED);
        }
    }

    private boolean hasShortCompletionCooldown(Long userId) {
        return !shortCooldownExternalIds.isEmpty()
                && shortCooldownExternalIds.contains(
                userService.findAuthenticatedUser(userId).externalId());
    }

    private Optional<AiMessageSendResult> findResentResult(Long conversationId, SendMessageRequest request) {
        Optional<AiMessage> userMessage = aiMessageService.findUserMessage(conversationId, request.clientMessageId());
        if (userMessage.isEmpty()) {
            return Optional.empty();
        }
        Optional<AiMessage> reply = aiMessageService.findReply(userMessage.get().getId());
        if (reply.isPresent()) {
            return Optional.of(AiMessageSendResult.replied(AiMessageResponse.from(reply.get())));
        }
        if (aiReplyJobService.isReplyInProgress(userMessage.get().getId())) {
            return Optional.of(AiMessageSendResult.accepted(AiMessageAcceptedResponse.from(userMessage.get())));
        }
        return Optional.empty();
    }

    // 접수에 성공하면 대화방 락은 백그라운드 작업이 답변을 끝낸 뒤 푼다
    private AiMessageSendResult acceptMessage(Long userId, Long conversationId, SendMessageRequest request,
                                              AiConversationLock.Handle lock) {
        // 앞의 확인과 락 획득 사이에 이전 시도가 답변을 저장했을 수 있기에
        Optional<AiMessage> sentMessage = aiMessageService.findUserMessage(conversationId, request.clientMessageId());
        Optional<AiMessage> reply = sentMessage.flatMap(message -> aiMessageService.findReply(message.getId()));
        if (reply.isPresent()) {
            aiConversationLock.unlock(lock);
            return AiMessageSendResult.replied(AiMessageResponse.from(reply.get()));
        }
        if (aiReplyJobService.existsReplyInProgress(conversationId)) {
            log.info("AI reply job already in progress. userId={}, conversationId={}", userId, conversationId);
            throw new TooManyRequestsException(messageRetryAfter.toSeconds());
        }
        if (replyQueueProperties.enabled()) {
            if (aiReplyQueueService.isFull()) {
                aiReplyQueueMetrics.recordQueueFull();
                log.info("AI reply queue full. userId={}, conversationId={}", userId, conversationId);
                throw new BusinessException(CommonErrorCode.COMMON_SERVICE_UNAVAILABLE);
            }
            AiReplyJobDispatch dispatch = sentMessage.isPresent()
                    ? restartReply(userId, conversationId, sentMessage.get(), lock)
                    : createUserMessage(userId, conversationId, request, lock);
            return enqueueReply(conversationId, dispatch);
        }
        AiReplyTaskExecutor.Slot slot = aiReplyTaskExecutor.tryReserve().orElseThrow(() -> {
            log.info("AI reply capacity exceeded. userId={}, conversationId={}", userId, conversationId);
            return new BusinessException(CommonErrorCode.COMMON_SERVICE_UNAVAILABLE);
        });
        try {
            AiReplyJobDispatch dispatch = sentMessage.isPresent()
                    ? restartReply(userId, conversationId, sentMessage.get(), lock)
                    : createUserMessage(userId, conversationId, request, lock);
            startReply(conversationId, dispatch, lock, slot);
            return AiMessageSendResult.accepted(AiMessageAcceptedResponse.from(dispatch.userMessage()));
        } catch (RuntimeException e) {
            slot.release();
            throw e;
        }
    }

    // 대화방 락은 워커가 작업 행의 락 토큰으로 이어받아 답변을 끝낸 뒤 푼다
    private AiMessageSendResult enqueueReply(Long conversationId, AiReplyJobDispatch dispatch) {
        aiGenerationStateService.startGenerating(conversationId, dispatch.userMessage().getId());
        aiReplyQueueService.enqueue(dispatch.jobId(), dispatch.dispatchToken());
        return AiMessageSendResult.accepted(AiMessageAcceptedResponse.from(dispatch.userMessage()));
    }

    private void startReply(Long conversationId, AiReplyJobDispatch dispatch,
                            AiConversationLock.Handle lock, AiReplyTaskExecutor.Slot slot) {
        Long userMessageId = dispatch.userMessage().getId();
        aiGenerationStateService.startGenerating(conversationId, userMessageId);
        try {
            slot.execute(() -> replyInBackground(conversationId, dispatch, lock));
        } catch (RuntimeException e) {
            recordReplyFailure(conversationId, userMessageId, e);
            aiGenerationStateService.finishGenerating(conversationId, userMessageId);
            throw e;
        }
    }

    // 지금 경로는 접수한 서버가 바로 첫 할당으로 처리
    private void replyInBackground(Long conversationId, AiReplyJobDispatch dispatch, AiConversationLock.Handle lock) {
        try {
            replyToJob(dispatch.jobId(), dispatch.dispatchToken(), FIRST_DELIVERY, null, AiReplyLease.unmanaged());
        } catch (RuntimeException e) {
            Long userMessageId = dispatch.userMessage().getId();
            log.error("AI reply job take failed. conversationId={}, userMessageId={}", conversationId, userMessageId, e);
            recordReplyFailure(conversationId, userMessageId, e);
            finishReply(conversationId, userMessageId, lock);
        }
    }

    // 큐 워커도 이 메서드로 작업 하나를 처리 => 두 경로가 같은 저장 규칙을 사용하도록
    public AiReplyJobOutcome replyToJob(Long jobId, String dispatchToken, long deliveryCount, String streamEntryId,
                                        AiReplyLease lease) {
        var processingSample = aiReplyQueueMetrics.startProcessing();
        AiReplyJobTakeResult job;
        try {
            job = aiReplyJobService.take(jobId, dispatchToken, deliveryCount, streamEntryId);
        } catch (RuntimeException e) {
            aiReplyQueueMetrics.recordProcessing(processingSample, AiReplyProcessingResult.RETRYABLE_FAILURE);
            throw e;
        }
        return switch (job.outcome()) {
            case STALE -> {
                log.info("AI reply job stale. jobId={}", jobId);
                yield AiReplyJobOutcome.FINISHED;
            }
            case ALREADY_SUCCEEDED -> {
                finishSucceededJob(job);
                yield AiReplyJobOutcome.FINISHED;
            }
            case GAVE_UP -> {
                aiReplyQueueMetrics.recordGaveUp();
                aiReplyQueueMetrics.recordProcessing(processingSample, AiReplyProcessingResult.TERMINAL_FAILURE);
                finishGivenUpJob(job);
                yield AiReplyJobOutcome.FINISHED;
            }
            case TAKEN -> {
                lease.attach(job.conversationId(), job.userMessageId(), job.lockToken());
                yield generateJobReply(job, lease, processingSample);
            }
        };
    }

    private void finishSucceededJob(AiReplyJobTakeResult job) {
        AiMessage reply = aiMessageService.findReply(job.userMessageId())
                .orElseThrow(() -> new IllegalStateException(
                        "AI reply not found for succeeded job. jobId=" + job.jobId()));
        finishReply(job.conversationId(), job.userMessageId(), toLock(job));
        AiChatTurnEvents.ofAcceptedTurn(aiChatEventPublisher, job.conversationId(), job.userMessageId())
                .complete(AiChatCompletedEvent.from(reply));
    }

    private void finishGivenUpJob(AiReplyJobTakeResult job) {
        finishReply(job.conversationId(), job.userMessageId(), toLock(job));
        AiChatTurnEvents.ofAcceptedTurn(aiChatEventPublisher, job.conversationId(), job.userMessageId())
                .fail(new BusinessException(CommonErrorCode.COMMON_SERVICE_UNAVAILABLE));
    }

    private AiReplyJobOutcome generateJobReply(AiReplyJobTakeResult job, AiReplyLease lease,
                                               Timer.Sample processingSample) {
        Long conversationId = job.conversationId();
        Long userMessageId = job.userMessageId();
        AiConversationLock.Handle lock = toLock(job);
        AiChatTurnEvents turnEvents = new AiChatTurnEvents(aiChatEventPublisher, conversationId);
        AiReplyCommitResult committed;
        try {
            if (!aiConversationLock.ensure(lock)) {
                // 다른 턴이 대화방 락을 잡음 => 그 턴의 락과 생성 중 표시는 건드리지 않는다
                log.info("AI reply lock held by another turn. jobId={}, conversationId={}, userMessageId={}",
                        job.jobId(), conversationId, userMessageId);
                boolean failureRecorded = recordTakenJobFailure(job, LOCK_LOST_ERROR_CODE);
                aiReplyQueueMetrics.recordProcessing(processingSample, failureRecorded
                        ? AiReplyProcessingResult.TERMINAL_FAILURE
                        : AiReplyProcessingResult.RETRYABLE_FAILURE);
                return toOutcome(failureRecorded);
            }
            turnEvents.accept(userMessageId, job.userMessage().getClientMessageId());
            aiChatRoomService.validateMessageSendableRoom(job.userId(), conversationId);
            AiServerSendMessageResponse response = requestLeasedReply(job, lease,
                    text -> publishDeltaWhileLeased(turnEvents, lease, text));
            committed = commitReply(job, response);
        } catch (RuntimeException e) {
            Thread.interrupted();
            if (lease.isLost()) {
                log.info("AI reply stopped after lease lost. jobId={}, attempt={}, conversationId={}",
                        job.jobId(), job.attempt(), conversationId);
                aiReplyQueueMetrics.recordProcessing(processingSample, AiReplyProcessingResult.RETRYABLE_FAILURE);
                return AiReplyJobOutcome.OWNERSHIP_LOST;
            }
            if (lease.isReturning()) {
                return returnJobToQueue(job, processingSample);
            }
            return failJobReply(job, lock, turnEvents, e, processingSample);
        }
        if (committed.isRejected()) {
            aiReplyQueueMetrics.recordCommitRejected();
            aiReplyQueueMetrics.recordProcessing(processingSample, AiReplyProcessingResult.RETRYABLE_FAILURE);
            return AiReplyJobOutcome.OWNERSHIP_LOST;
        }
        aiReplyQueueMetrics.recordProcessing(processingSample, AiReplyProcessingResult.SUCCESS);
        finishReply(conversationId, userMessageId, lock);
        turnEvents.complete(AiChatCompletedEvent.from(committed.reply()));
        return AiReplyJobOutcome.FINISHED;
    }

    private void publishDeltaWhileLeased(AiChatTurnEvents turnEvents, AiReplyLease lease, String text) {
        if (lease.isLost()) {
            throw new IllegalStateException("AI reply lease lost");
        }
        turnEvents.publishDelta(text);
    }

    // 회수한 워커가 부를 때 앞 워커의 턴이 아직 열려 있으면 AI 서버가 바로 거절한다 => 앞 턴이 끝날 때까지 다시 부른다
    private AiServerSendMessageResponse requestLeasedReply(AiReplyJobTakeResult job, AiReplyLease lease,
                                                           Consumer<String> onDelta) {
        Instant deadline = clock.instant().plus(turnTimeout);
        Duration delay = replyQueueProperties.turnInProgressRetryDelay();
        for (int retry = 1; ; retry++) {
            validateLeaseHeld(job, lease);
            try {
                return requestReply(job.userId(), job.conversationId(), job.userMessage(), onDelta);
            } catch (BusinessException e) {
                if (!isRetryableTurnInProgress(e, lease, deadline, delay)) {
                    throw e;
                }
                log.info("AI reply turn in progress, retrying. jobId={}, attempt={}, retry={}",
                        job.jobId(), job.attempt(), retry);
                aiReplyQueueMetrics.recordTurnInProgressRetry();
                sleepBeforeRetry(delay, e);
                delay = delay.multipliedBy(2);
            }
        }
    }

    // 멈췄다 깨어난 워커가 이미 회수된 작업으로 AI를 부르지 않게
    private void validateLeaseHeld(AiReplyJobTakeResult job, AiReplyLease lease) {
        if (!lease.isManaged()) {
            return;
        }
        if (lease.isLost()
                || !lease.isConfirmedWithin(maxLeaseSilence)
                || !aiReplyJobService.isOwnedBy(job.jobId(), job.attempt())) {
            lease.markLost();
            throw new IllegalStateException("AI reply lease lost");
        }
    }

    private boolean isRetryableTurnInProgress(BusinessException e, AiReplyLease lease, Instant deadline,
                                              Duration delay) {
        return lease.isManaged()
                && e.getErrorCode() == AiChatErrorCode.AICHAT_TURN_IN_PROGRESS
                && !clock.instant().plus(delay).isAfter(deadline);
    }

    private void sleepBeforeRetry(Duration delay, BusinessException cause) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw cause;
        }
    }

    private AiReplyJobOutcome toOutcome(boolean failureRecorded) {
        return failureRecorded ? AiReplyJobOutcome.FINISHED : AiReplyJobOutcome.OWNERSHIP_LOST;
    }

    private AiReplyCommitResult commitReply(AiReplyJobTakeResult job, AiServerSendMessageResponse response) {
        try {
            AiReplyCommitResult committed = aiReplyJobService.commitReply(job.jobId(), job.attempt(),
                    response.reply(), response.progress(), response.inputLocked(),
                    toPurgeAt(response.expirationAt()));
            if (!committed.isRejected()) {
                log.info("AI message replied. userId={}, conversationId={}, messageId={}",
                        job.userId(), job.conversationId(), committed.reply().getId());
            }
            return committed;
        } catch (DataIntegrityViolationException e) {
            AiMessage savedReply = aiMessageService.findReply(job.userMessageId()).orElseThrow(() -> e);
            log.info("AI reply already saved. userId={}, conversationId={}, userMessageId={}, messageId={}",
                    job.userId(), job.conversationId(), job.userMessageId(), savedReply.getId());
            return AiReplyCommitResult.saved(savedReply);
        }
    }

    // 다른 인스턴스가 이어서 답한다 => 오류를 보내지 않고, 대화방 락과 생성 중 표시도 그 워커가 이어받도록 둔다
    private AiReplyJobOutcome returnJobToQueue(AiReplyJobTakeResult job, Timer.Sample processingSample) {
        Optional<String> dispatchToken = aiReplyJobService.returnTakenJob(job.jobId(), job.attempt());
        if (dispatchToken.isEmpty()) {
            aiReplyQueueMetrics.recordProcessing(processingSample, AiReplyProcessingResult.RETRYABLE_FAILURE);
            return AiReplyJobOutcome.OWNERSHIP_LOST;
        }
        aiReplyQueueMetrics.recordReturned();
        aiReplyQueueMetrics.recordProcessing(processingSample, AiReplyProcessingResult.RETRYABLE_FAILURE);
        aiReplyQueueService.enqueue(job.jobId(), dispatchToken.get());
        return AiReplyJobOutcome.RETURNED;
    }

    private AiReplyJobOutcome failJobReply(AiReplyJobTakeResult job, AiConversationLock.Handle lock,
                                           AiChatTurnEvents turnEvents, RuntimeException cause,
                                           Timer.Sample processingSample) {
        logReplyFailure(job.userId(), job.conversationId(), job.userMessageId(), cause);
        if (!recordTakenJobFailure(job, toErrorCode(cause))) {
            aiReplyQueueMetrics.recordProcessing(processingSample, AiReplyProcessingResult.RETRYABLE_FAILURE);
            return AiReplyJobOutcome.OWNERSHIP_LOST;
        }
        aiReplyQueueMetrics.recordProcessing(processingSample, AiReplyProcessingResult.TERMINAL_FAILURE);
        finishReply(job.conversationId(), job.userMessageId(), lock);
        turnEvents.fail(cause);
        return AiReplyJobOutcome.FINISHED;
    }

    // 기록이 실패하면(DB 장애) 처리 권한을 확인할 수 없다 => 사용자가 계속 기다리지 않도록 마무리는 진행한다
    private boolean recordTakenJobFailure(AiReplyJobTakeResult job, String errorCode) {
        try {
            return aiReplyJobService.failTakenJob(job.jobId(), job.attempt(), errorCode);
        } catch (RuntimeException e) {
            log.warn("AI reply job failure record failed. conversationId={}, userMessageId={}",
                    job.conversationId(), job.userMessageId(), e);
            return true;
        }
    }

    private AiConversationLock.Handle toLock(AiReplyJobTakeResult job) {
        return new AiConversationLock.Handle(job.conversationId(), job.lockToken());
    }

    private void finishReply(Long conversationId, Long userMessageId, AiConversationLock.Handle lock) {
        aiGenerationStateService.finishGenerating(conversationId, userMessageId);
        aiConversationLock.unlock(lock);
    }

    private void recordReplyFailure(Long conversationId, Long userMessageId, RuntimeException cause) {
        try {
            aiReplyJobService.failJob(userMessageId, toErrorCode(cause));
        } catch (RuntimeException e) {
            log.warn("AI reply job failure record failed. conversationId={}, userMessageId={}",
                    conversationId, userMessageId, e);
        }
    }

    private String toErrorCode(RuntimeException cause) {
        return cause instanceof BusinessException businessException
                ? businessException.getErrorCode().name()
                : CommonErrorCode.COMMON_INTERNAL_SERVER_ERROR.name();
    }

    private void logReplyFailure(Long userId, Long conversationId, Long userMessageId, RuntimeException e) {
        if (!(e instanceof BusinessException businessException)) {
            log.error("AI reply failed unexpectedly. userId={}, conversationId={}, userMessageId={}",
                    userId, conversationId, userMessageId, e);
            return;
        }
        ErrorCode errorCode = businessException.getErrorCode();
        if (errorCode.getStatus().is5xxServerError()) {
            log.warn("AI reply failed. userId={}, conversationId={}, userMessageId={}, code={}",
                    userId, conversationId, userMessageId, errorCode.name(), e);
            return;
        }
        log.info("AI reply failed. userId={}, conversationId={}, userMessageId={}, code={}",
                userId, conversationId, userMessageId, errorCode.name());
    }

    private AiReplyJobDispatch restartReply(Long userId, Long conversationId, AiMessage userMessage,
                                            AiConversationLock.Handle lock) {
        log.info("AI reply regenerating for unanswered message. userId={}, conversationId={}, userMessageId={}",
                userId, conversationId, userMessage.getId());
        return aiReplyJobService.restartJob(conversationId, userMessage, userId, lock.ownerToken());
    }

    private AiReplyJobDispatch createUserMessage(Long userId, Long conversationId, SendMessageRequest request,
                                                 AiConversationLock.Handle lock) {
        try {
            return aiReplyJobService.createUserMessageAndJob(
                    conversationId, userId, request.clientMessageId(), request.content(), lock.ownerToken());
        } catch (DataIntegrityViolationException e) {
            // 다른 인스턴스가 같은 clientMessageId를 이미 처리 중 => 대화방 락이 막지 못하는 경우
            throw new TooManyRequestsException(messageRetryAfter.toSeconds());
        }
    }

    private AiServerSendMessageResponse requestReply(Long userId, Long conversationId, AiMessage userMessage,
                                                     Consumer<String> onDelta) {
        try {
            AiServerSendMessageResponse response = aiChatClient.streamMessage(
                    userId, conversationId, userMessage.getId(), userMessage.getContent(), onDelta);
            validateReply(response);
            return response;
        } catch (BusinessException e) {
            expireRoomIfSessionGone(userId, conversationId, e);
            throw e;
        }
    }

    // AI 세션이 만료 규칙보다 일찍 사라졌거나 이미 닫힘 => 방을 정리해 새 대화를 시작하게 한다
    private void expireRoomIfSessionGone(Long userId, Long conversationId, BusinessException e) {
        if (e.getErrorCode() instanceof AiChatErrorCode errorCode && errorCode.isSessionGone()) {
            log.info("AI chat session gone. userId={}, conversationId={}, code={}",
                    userId, conversationId, errorCode.name());
            aiChatRoomService.expireRoom(conversationId);
        }
    }

    private void validateReply(AiServerSendMessageResponse response) {
        if (response.reply() == null
                || response.reply().isBlank()
                || response.progress() == null
                || response.progress() < 0
                || response.progress() > 100
                || response.inputLocked() == null
                || response.expirationAt() == null) {
            throw new BusinessException(AiChatErrorCode.AICHAT_INVALID_RESPONSE);
        }
    }

    private AiConversationStartResult restartConversation(Long userId, Long expiredRoomId, boolean hasShortCooldown) {
        log.info("AI chat session expired. userId={}, conversationId={}", userId, expiredRoomId);
        aiChatRoomService.expireRoom(expiredRoomId);
        AiChatRoom newRoom = hasShortCooldown
                ? aiChatRoomService.reserveRoomWithoutDailyLimit(userId)
                : aiChatRoomService.reserveRoom(userId);
        return startConversation(userId, newRoom.getId());
    }

    private AiConversationStartResult startConversation(Long userId, Long roomId) {
        try {
            AiServerUserProfileRequest userProfile =
                    AiServerUserProfileRequest.from(userService.findUserAiProfile(userId));
            AiServerStartSessionResponse session = requestStartSession(userId, roomId, userProfile);
            validateStartSession(roomId, session);
            AiChatRoom room = aiChatRoomService.activateRoom(
                    roomId, session.greeting(), toPurgeAt(session.expirationAt()));
            log.info("AI conversation started. userId={}, conversationId={}", userId, roomId);
            return AiConversationStartResult.created(AiConversationResponse.from(room, 0));
        } catch (RuntimeException e) {
            // 실패한 방이 PENDING으로 남으면 재시도가 막히므로 즉시 정리
            aiChatRoomService.discardRoom(roomId);
            throw e;
        }
    }

    private AiServerStartSessionResponse requestStartSession(Long userId, Long roomId,
                                                             AiServerUserProfileRequest userProfile) {
        Map<String, Object> memory = userService.findAiMemory(userId);
        if (memory.isEmpty()) {
            return aiChatClient.startSession(userId, roomId, userProfile, null);
        }
        try {
            return aiChatClient.startSession(userId, roomId, userProfile, memory);
        } catch (BusinessException e) {
            if (e.getErrorCode() != AiChatErrorCode.AICHAT_MEMORY_REJECTED) {
                throw e;
            }
            // 기억이 깨져도 대화는 시작할 수 있어야 한다 => 기억 없이 한 번만 다시 요청, 원인은 AI 서버 로그로 확인
            log.warn("AI memory rejected. Retrying without memory. userId={}, conversationId={}", userId, roomId);
            return aiChatClient.startSession(userId, roomId, userProfile, null);
        }
    }

    private void validateStartSession(Long roomId, AiServerStartSessionResponse session) {
        if (!roomId.equals(session.conversationRoomId())
                || session.greeting() == null
                || session.greeting().isBlank()
                || session.expirationAt() == null) {
            throw new BusinessException(AiChatErrorCode.AICHAT_INVALID_RESPONSE);
        }
    }

    private LocalDateTime toPurgeAt(OffsetDateTime expirationAt) {
        if (expirationAt == null) {
            throw new BusinessException(AiChatErrorCode.AICHAT_INVALID_RESPONSE);
        }
        return expirationAt.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }


    public AnalysisResultResponse createAnalysis(Long userId, Long conversationId) {
        userService.validateActiveUser(userId);
        aiChatRoomService.validateAnalyzableRoom(userId, conversationId);

        if (aiConversationLock.isLocked(conversationId)) {
            log.info("AI analysis requested while confirming. userId={}, conversationId={}", userId, conversationId);
            throw new BusinessException(AiChatErrorCode.AICHAT_ANALYSIS_ALREADY_CONFIRMED);
        }
        try {
            return toAnalysisResult(aiChatClient.createAnalysis(conversationId));
        } catch (BusinessException e) {
            if (e.getErrorCode() == AiChatErrorCode.AICHAT_SESSION_CLOSED) {
                log.info("AI analysis requested after session closed. userId={}, conversationId={}",
                        userId, conversationId);
                throw new BusinessException(AiChatErrorCode.AICHAT_ANALYSIS_ALREADY_CONFIRMED, null, e);
            }
            expireRoomIfSessionGone(userId, conversationId, e);
            if (e.getErrorCode() == AiChatErrorCode.AICHAT_PROFILE_TOO_SPARSE) {
                throw expireSparseRoom(userId, conversationId, e);
            }
            throw e;
        }
    }

    // AI 세션은 입력이 잠긴 채 분석도 거절해 다시 요청해도 결과가 같다 => ANALYZING에 묶이지 않도록 만료해 새 대화를 시작하게 한다
    private BusinessException expireSparseRoom(Long userId, Long conversationId, BusinessException cause) {
        log.info("AI analysis rejected for sparse profile. userId={}, conversationId={}", userId, conversationId);
        aiChatRoomService.expireRoom(conversationId);
        return new BusinessException(AiChatErrorCode.AICHAT_PROFILE_TOO_SPARSE,
                new RestartRequiredResponse(true), cause);
    }

    public AnalysisResultResponse patchAnalyze(Long userId, Long conversationId, PatchAnalyzeMessageRequest request) {
        userService.validateActiveUser(userId);
        aiChatRoomService.validateActiveRoom(userId, conversationId);
        try {
            return toAnalysisResult(aiChatClient.patchAnalyze(conversationId,
                    new AiServerPatchAnalysisRequest(
                            userId,
                            request.summary(),
                            new AiServerAnalysisKeywordsRequest(
                                    request.keywords().taste(), request.keywords().interest()))));
        } catch (BusinessException e) {
            expireRoomIfSessionGone(userId, conversationId, e);
            throw e;
        }
    }

    public ProductRecommendationStatusResponse confirmAnalysis(Long userId, Long conversationId) {
        userService.validateActiveUser(userId);
        aiChatRoomService.validateActiveRoom(userId, conversationId);
        // AI 세션 종료는 되돌릴 수 없으므로 같은 대화방의 확정 요청이 겹쳐 AI를 두 번 호출하지 않게 막는다
        Optional<AiConversationLock.Handle> lock = aiConversationLock.tryLock(conversationId);
        if (lock.isEmpty()) {
            log.info("AI analysis confirm already in progress. userId={}, conversationId={}", userId, conversationId);
            throw new TooManyRequestsException(messageRetryAfter.toSeconds());
        }
        try {
            return closeSessionAndSaveAnalysis(userId, conversationId);
        } finally {
            aiConversationLock.unlock(lock.get());
        }
    }

    private ProductRecommendationStatusResponse closeSessionAndSaveAnalysis(Long userId, Long conversationId) {
        // 조회가 실패해도 대화방이 ANALYZING에 묶이지 않도록 상태를 바꾸기 전에 모은다
        AiServerFeedbackRequest feedback = findFeedback(userId);
        aiChatRoomService.startAnalysis(conversationId);
        AiServerCloseSessionResponse response;
        try {
            response = requestCloseSession(userId, conversationId, feedback);
        } catch (BusinessException e) {
            expireRoomIfSessionGone(userId, conversationId, e);
            throw e;
        }
        try {
            validateCloseResponse(response);
            aiAnalysisConfirmService.saveConfirmedAnalysis(userId, conversationId, response);
        } catch (RuntimeException e) {
            expireClosedRoom(userId, conversationId, e);
            throw e;
        }
        return ProductRecommendationStatusResponse.success();
    }

    private AiServerCloseSessionResponse requestCloseSession(Long userId, Long conversationId,
                                                             AiServerFeedbackRequest feedback) {
        if (feedback == null) {
            return aiChatClient.confirmAnalysis(userId, conversationId, null);
        }
        try {
            return aiChatClient.confirmAnalysis(userId, conversationId, feedback);
        } catch (BusinessException e) {
            if (e.getErrorCode() != AiChatErrorCode.AICHAT_FEEDBACK_REJECTED) {
                throw e;
            }
            log.warn("AI feedback rejected. Retrying without feedback. userId={}, conversationId={}",
                    userId, conversationId);
            return aiChatClient.confirmAnalysis(userId, conversationId, null);
        }
    }

    private AiServerFeedbackRequest findFeedback(Long userId) {
        List<ProductActivityResponse> activities = productActivityService.findAllRecentActivities(userId);
        return activities.isEmpty() ? null : AiServerFeedbackRequest.from(activities);
    }

    // AI 세션은 이미 닫혀 같은 대화방으로는 다시 확정할 수 없다 => ANALYZING에 묶이지 않도록 만료해 새 대화를 시작하게 한다
    private void expireClosedRoom(Long userId, Long conversationId, RuntimeException cause) {
        log.warn("AI analysis result not saved after session closed. userId={}, conversationId={}",
                userId, conversationId);
        try {
            aiChatRoomService.expireRoom(conversationId);
        } catch (RuntimeException expireFailure) {
            cause.addSuppressed(expireFailure);
        }
    }

    private void validateCloseResponse(AiServerCloseSessionResponse response) {
        if (response.recommendations() == null
                || response.recommendations().self() == null
                || response.recommendations().self().items() == null
                || response.recommendations().gift() == null
                || response.recommendations().gift().items() == null
                || response.recommendations().self().items().stream()
                        .anyMatch(item -> item == null || item.score() == null)
                || response.recommendations().gift().items().stream()
                        .anyMatch(item -> item == null || item.score() == null)
                || response.summary() == null
                || response.summary().isBlank()
                || response.keywords() == null
                || hasInvalidFinalKeywords(response.keywords().taste())
                || hasInvalidFinalKeywords(response.keywords().interest())) {
            throw new BusinessException(AiChatErrorCode.AICHAT_INVALID_RESPONSE);
        }
    }

    private boolean hasInvalidFinalKeywords(List<AiServerAnalysisKeywordResponse> keywords) {
        return keywords == null || keywords.size() > 3 || hasInvalidKeyword(keywords);
    }

    private AnalysisResultResponse toAnalysisResult(AiServerAnalysisResponse response) {
        if (response.profile() == null
                || response.profile().summary() == null
                || response.profile().summary().isBlank()
                || response.profile().keywords() == null
                || hasInvalidKeyword(response.profile().keywords().taste())
                || hasInvalidKeyword(response.profile().keywords().interest())
                || response.profile().correctionAvailable() == null) {
            throw new BusinessException(AiChatErrorCode.AICHAT_INVALID_RESPONSE);
        }
        return AnalysisResultResponse.from(response);
    }

    private boolean hasInvalidKeyword(List<AiServerAnalysisKeywordResponse> keywords) {
        return keywords == null || keywords.stream().anyMatch(keyword -> keyword == null
                || keyword.value() == null
                || keyword.value().isBlank()
                || keyword.score() == null
                || !Double.isFinite(keyword.score())
                || keyword.score() < 0
                || keyword.score() > 1);
    }
}
