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
import kr.ktb.zura.needu.aichat.client.AiChatReplyStreamClient;
import kr.ktb.zura.needu.aichat.client.dto.request.AiServerAnalysisKeywordsRequest;
import kr.ktb.zura.needu.aichat.client.dto.request.AiServerPatchAnalysisRequest;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerCloseSessionResponse;
import kr.ktb.zura.needu.aichat.dto.request.PatchAnalyzeMessageRequest;
import kr.ktb.zura.needu.aichat.dto.request.SendMessageRequest;
import kr.ktb.zura.needu.aichat.dto.response.AiConversationResponse;
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
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.TooManyRequestsException;
import kr.ktb.zura.needu.common.response.CursorPageResponse;
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
    private final AiChatReplyStreamClient aiChatReplyStreamClient;
    private final AiAnalysisConfirmService aiAnalysisConfirmService;
    private final AiChatStreamService aiChatStreamService;
    private final AiChatEventPublisher aiChatEventPublisher;
    private final UserService userService;
    private final Duration messageRetryAfter;
    private final Duration shortCompletionCooldown;
    private final Set<Long> shortCooldownExternalIds;

    public AiChatFacade(AiChatRoomService aiChatRoomService,
                        AiMessageService aiMessageService,
                        AiConversationLock aiConversationLock,
                        AiGenerationStateService aiGenerationStateService,
                        AiChatClient aiChatClient,
                        AiChatReplyStreamClient aiChatReplyStreamClient,
                        AiAnalysisConfirmService aiAnalysisConfirmService,
                        AiChatStreamService aiChatStreamService,
                        AiChatEventPublisher aiChatEventPublisher,
                        UserService userService,
                        @Value("${ai.chat.message-retry-after:10s}") Duration messageRetryAfter,
                        @Value("${ai.chat.short-completion-cooldown:5m}") Duration shortCompletionCooldown,
                        @Value("${ai.chat.short-cooldown-external-ids:}") Set<Long> shortCooldownExternalIds) {
        this.aiChatRoomService = aiChatRoomService;
        this.aiMessageService = aiMessageService;
        this.aiConversationLock = aiConversationLock;
        this.aiGenerationStateService = aiGenerationStateService;
        this.aiChatClient = aiChatClient;
        this.aiChatReplyStreamClient = aiChatReplyStreamClient;
        this.aiAnalysisConfirmService = aiAnalysisConfirmService;
        this.aiChatStreamService = aiChatStreamService;
        this.aiChatEventPublisher = aiChatEventPublisher;
        this.userService = userService;
        this.messageRetryAfter = messageRetryAfter;
        this.shortCompletionCooldown = shortCompletionCooldown;
        this.shortCooldownExternalIds = shortCooldownExternalIds;
    }

    public AiConversationStartResult startOrResumeConversation(Long userId) {
        userService.validateActiveUser(userId);
        // TODO: AI 정보 활용 동의 여부 확인(403) — 동의 저장 방식 확정 후 구현 (V2)
        boolean hasShortCooldown = hasShortCompletionCooldown(userId);
        AiChatRoom room = hasShortCooldown
                ? aiChatRoomService.findOrReserveRoom(userId, shortCompletionCooldown)
                : aiChatRoomService.findOrReserveRoom(userId);
        if (room.isPending()) {
            return startConversation(userId, room.getId());
        }
        // AI 서버에 세션 조회 API가 없어 purgeAt으로 만료를 판단, 규칙보다 일찍 사라진 세션은 메시지 전송·분석 시 발견
        // ANALYZING도 AI 세션이 만료되면 분석을 이어갈 수 없음 => 다른 대화 API처럼 만료로 보고 새 대화를 시작한다
        if (!room.isCompleted() && room.isExpiredAt(LocalDateTime.now(ZoneOffset.UTC))) {
            return restartConversation(userId, room.getId());
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
        // TODO: AI 정보 활용 동의 여부 확인(403) — 동의 저장 방식 확정 후 구현 (V2)
        AiChatRoom room = aiChatRoomService.findStreamableRoom(userId, conversationId);
        return aiChatStreamService.connect(room);
    }

    public AiMessageResponse sendMessage(Long userId, Long conversationId, SendMessageRequest request) {
        userService.validateActiveUser(userId);
        // TODO: AI 정보 활용 동의 여부 확인(403) — 동의 저장 방식 확정 후 구현 (V2)
        aiChatRoomService.validateMessageSendableRoom(userId, conversationId);
        Optional<AiConversationLock.Handle> lock = aiConversationLock.tryLock(conversationId);
        if (lock.isEmpty()) {
            log.info("AI message already in progress. userId={}, conversationId={}", userId, conversationId);
            throw new TooManyRequestsException(messageRetryAfter.toSeconds());
        }
        AiChatTurnEvents turnEvents = new AiChatTurnEvents(aiChatEventPublisher, conversationId);
        AiMessageResponse response;
        try {
            response = replyToUserMessage(userId, conversationId, request, turnEvents);
        } catch (RuntimeException e) {
            aiConversationLock.unlock(lock.get());
            turnEvents.fail(e);
            throw e;
        }

        aiConversationLock.unlock(lock.get());
        turnEvents.complete(response);
        return response;
    }

    private boolean hasShortCompletionCooldown(Long userId) {
        return !shortCooldownExternalIds.isEmpty()
                && shortCooldownExternalIds.contains(
                userService.findAuthenticatedUser(userId).externalId());
    }

    private AiMessageResponse replyToUserMessage(Long userId, Long conversationId, SendMessageRequest request,
                                                 AiChatTurnEvents turnEvents) {
        Optional<AiMessage> sentMessage = aiMessageService.findUserMessage(conversationId, request.clientMessageId());
        if (sentMessage.isEmpty()) {
            return generateReply(userId, conversationId, createUserMessage(conversationId, request), turnEvents);
        }
        return replyToResentMessage(userId, conversationId, sentMessage.get(), turnEvents);
    }

    // 같은 clientMessageId로 다시 들어온 요청 => 저장된 답변 ~> 생성 중 상태 순으로 확인해서 처리
    private AiMessageResponse replyToResentMessage(Long userId, Long conversationId, AiMessage userMessage,
                                                   AiChatTurnEvents turnEvents) {
        Optional<AiMessage> reply = aiMessageService.findReply(userMessage.getId());
        if (reply.isPresent()) {
            return AiMessageResponse.from(reply.get());
        }
        if (aiGenerationStateService.isGenerating(conversationId, userMessage.getId())) {
            throw new TooManyRequestsException(messageRetryAfter.toSeconds());
        }

        log.info("AI reply regenerating for unanswered message. userId={}, conversationId={}, userMessageId={}",
                userId, conversationId, userMessage.getId());
        return generateReply(userId, conversationId, userMessage, turnEvents);
    }

    private AiMessageResponse generateReply(Long userId, Long conversationId, AiMessage userMessage,
                                            AiChatTurnEvents turnEvents) {
        Long userMessageId = userMessage.getId();
        aiGenerationStateService.startGenerating(conversationId, userMessageId);
        turnEvents.accept(userMessageId, userMessage.getClientMessageId());
        try {
            AiServerSendMessageResponse response = requestReply(
                    userId, conversationId, userMessage.getContent(), turnEvents::publishDelta);
            AiMessage aiMessage = aiMessageService.createReplyAndUpdateRoom(
                    conversationId, userMessageId, response.reply(), response.progress(),
                    response.inputLocked(), toPurgeAt(response.expirationAt()));
            log.info("AI message replied. userId={}, conversationId={}, messageId={}",
                    userId, conversationId, aiMessage.getId());
            return AiMessageResponse.from(aiMessage);
        } finally {
            aiGenerationStateService.finishGenerating(conversationId, userMessageId);
        }
    }

    private AiMessage createUserMessage(Long conversationId, SendMessageRequest request) {
        try {
            return aiMessageService.createUserMessage(conversationId, request.clientMessageId(), request.content());
        } catch (DataIntegrityViolationException e) {
            // 다른 인스턴스가 같은 clientMessageId를 이미 처리 중 => 대화방 락이 막지 못하는 경우
            throw new TooManyRequestsException(messageRetryAfter.toSeconds());
        }
    }

    private AiServerSendMessageResponse requestReply(Long userId, Long conversationId, String content,
                                                     Consumer<String> onDelta) {
        try {
            AiServerSendMessageResponse response = aiChatReplyStreamClient.streamMessage(
                    userId, conversationId, content, onDelta);
            validateReply(response);
            return response;
        } catch (BusinessException e) {
            if (!(e.getErrorCode() instanceof AiChatErrorCode errorCode)) {
                throw e;
            }
            expireRoomIfSessionGone(userId, conversationId, e);
            if (errorCode == AiChatErrorCode.AICHAT_TURN_IN_PROGRESS) {
                // AI 서버가 이전 메시지를 처리 중 => 대화방을 그대로 두고 재시도 시각만 알려준다
                log.info("AI turn in progress. userId={}, conversationId={}", userId, conversationId);
                throw new TooManyRequestsException(messageRetryAfter.toSeconds());
            }
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

    private AiConversationStartResult restartConversation(Long userId, Long expiredRoomId) {
        log.info("AI chat session expired. userId={}, conversationId={}", userId, expiredRoomId);
        AiChatRoom newRoom = aiChatRoomService.expireAndReserveRoom(userId, expiredRoomId);
        return startConversation(userId, newRoom.getId());
    }

    private AiConversationStartResult startConversation(Long userId, Long roomId) {
        try {
            AiServerStartSessionResponse session = aiChatClient.startSession(userId, roomId);
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
