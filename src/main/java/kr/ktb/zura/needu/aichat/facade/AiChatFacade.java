package kr.ktb.zura.needu.aichat.facade;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import kr.ktb.zura.needu.aichat.client.AiChatClient;
import kr.ktb.zura.needu.aichat.client.dto.request.AiServerAnalysisKeywordsRequest;
import kr.ktb.zura.needu.aichat.client.dto.request.AiServerPatchAnalysisRequest;
import kr.ktb.zura.needu.aichat.client.dto.request.AiServerUserProfileRequest;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerCloseSessionResponse;
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
import kr.ktb.zura.needu.aichat.service.AiReplyTaskExecutor;
import kr.ktb.zura.needu.aichat.sse.AiChatCompletedEvent;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.common.exception.ErrorCode;
import kr.ktb.zura.needu.common.exception.TooManyRequestsException;
import kr.ktb.zura.needu.common.response.CursorPageResponse;
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

    private final AiChatRoomService aiChatRoomService;
    private final AiMessageService aiMessageService;
    private final AiConversationLock aiConversationLock;
    private final AiGenerationStateService aiGenerationStateService;
    private final AiChatClient aiChatClient;
    private final AiAnalysisConfirmService aiAnalysisConfirmService;
    private final AiChatStreamService aiChatStreamService;
    private final AiChatEventPublisher aiChatEventPublisher;
    private final AiReplyTaskExecutor aiReplyTaskExecutor;
    private final UserService userService;
    private final ConsentService consentService;
    private final Duration messageRetryAfter;
    private final Duration shortCompletionCooldown;
    private final Set<Long> shortCooldownExternalIds;

    public AiChatFacade(AiChatRoomService aiChatRoomService,
                        AiMessageService aiMessageService,
                        AiConversationLock aiConversationLock,
                        AiGenerationStateService aiGenerationStateService,
                        AiChatClient aiChatClient,
                        AiAnalysisConfirmService aiAnalysisConfirmService,
                        AiChatStreamService aiChatStreamService,
                        AiChatEventPublisher aiChatEventPublisher,
                        AiReplyTaskExecutor aiReplyTaskExecutor,
                        UserService userService,
                        ConsentService consentService,
                        @Value("${ai.chat.message-retry-after:10s}") Duration messageRetryAfter,
                        @Value("${ai.chat.short-completion-cooldown:5m}") Duration shortCompletionCooldown,
                        @Value("${ai.chat.short-cooldown-external-ids:}") Set<Long> shortCooldownExternalIds) {
        this.aiChatRoomService = aiChatRoomService;
        this.aiMessageService = aiMessageService;
        this.aiConversationLock = aiConversationLock;
        this.aiGenerationStateService = aiGenerationStateService;
        this.aiChatClient = aiChatClient;
        this.aiAnalysisConfirmService = aiAnalysisConfirmService;
        this.aiChatStreamService = aiChatStreamService;
        this.aiChatEventPublisher = aiChatEventPublisher;
        this.aiReplyTaskExecutor = aiReplyTaskExecutor;
        this.userService = userService;
        this.consentService = consentService;
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
        if (aiGenerationStateService.isGenerating(conversationId, userMessage.get().getId())) {
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
        AiReplyTaskExecutor.Slot slot = aiReplyTaskExecutor.tryReserve().orElseThrow(() -> {
            log.info("AI reply capacity exceeded. userId={}, conversationId={}", userId, conversationId);
            return new BusinessException(CommonErrorCode.COMMON_SERVICE_UNAVAILABLE);
        });
        try {
            AiMessage userMessage = sentMessage.isPresent()
                    ? sentMessage.get()
                    : createUserMessage(conversationId, request);
            if (sentMessage.isPresent()) {
                log.info("AI reply regenerating for unanswered message. userId={}, conversationId={}, userMessageId={}",
                        userId, conversationId, userMessage.getId());
            }
            startReply(userId, conversationId, userMessage, lock, slot);
            return AiMessageSendResult.accepted(AiMessageAcceptedResponse.from(userMessage));
        } catch (RuntimeException e) {
            slot.release();
            throw e;
        }
    }

    private void startReply(Long userId, Long conversationId, AiMessage userMessage,
                            AiConversationLock.Handle lock, AiReplyTaskExecutor.Slot slot) {
        aiGenerationStateService.startGenerating(conversationId, userMessage.getId());
        try {
            slot.execute(() -> replyInBackground(userId, conversationId, userMessage, lock));
        } catch (RuntimeException e) {
            aiGenerationStateService.finishGenerating(conversationId, userMessage.getId());
            throw e;
        }
    }

    private void replyInBackground(Long userId, Long conversationId, AiMessage userMessage,
                                   AiConversationLock.Handle lock) {
        AiChatTurnEvents turnEvents = new AiChatTurnEvents(aiChatEventPublisher, conversationId);
        AiMessage reply;
        try {
            reply = generateReply(userId, conversationId, userMessage, turnEvents);
        } catch (RuntimeException e) {
            Thread.interrupted();
            logReplyFailure(userId, conversationId, userMessage.getId(), e);
            finishReply(conversationId, userMessage.getId(), lock);
            turnEvents.fail(e);
            return;
        }
        finishReply(conversationId, userMessage.getId(), lock);
        turnEvents.complete(AiChatCompletedEvent.from(reply));
    }

    private AiMessage generateReply(Long userId, Long conversationId, AiMessage userMessage,
                                    AiChatTurnEvents turnEvents) {
        turnEvents.accept(userMessage.getId(), userMessage.getClientMessageId());
        AiServerSendMessageResponse response = requestReply(
                userId, conversationId, userMessage, turnEvents::publishDelta);
        AiMessage aiMessage = aiMessageService.createReplyAndUpdateRoom(
                conversationId, userMessage.getId(), response.reply(), response.progress(),
                response.inputLocked(), toPurgeAt(response.expirationAt()));
        log.info("AI message replied. userId={}, conversationId={}, messageId={}",
                userId, conversationId, aiMessage.getId());
        return aiMessage;
    }

    private void finishReply(Long conversationId, Long userMessageId, AiConversationLock.Handle lock) {
        aiGenerationStateService.finishGenerating(conversationId, userMessageId);
        aiConversationLock.unlock(lock);
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

    private AiMessage createUserMessage(Long conversationId, SendMessageRequest request) {
        try {
            return aiMessageService.createUserMessage(conversationId, request.clientMessageId(), request.content());
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
            AiServerStartSessionResponse session = aiChatClient.startSession(userId, roomId, userProfile);
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
        try {
            return toAnalysisResult(aiChatClient.createAnalysis(conversationId));
        } catch (BusinessException e) {
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
        aiChatRoomService.startAnalysis(conversationId);
        AiServerCloseSessionResponse response;
        try {
            response = aiChatClient.confirmAnalysis(userId, conversationId);
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
