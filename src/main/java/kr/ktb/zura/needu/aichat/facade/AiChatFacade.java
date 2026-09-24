package kr.ktb.zura.needu.aichat.facade;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import kr.ktb.zura.needu.aichat.client.AiChatClient;
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
import kr.ktb.zura.needu.aichat.entity.AiChatRoom;
import kr.ktb.zura.needu.aichat.entity.AiMessage;
import kr.ktb.zura.needu.aichat.exception.AiChatErrorCode;
import kr.ktb.zura.needu.aichat.service.AiChatRoomService;
import kr.ktb.zura.needu.aichat.service.AiConversationLock;
import kr.ktb.zura.needu.aichat.service.AiMessageService;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.TooManyRequestsException;
import kr.ktb.zura.needu.common.response.CursorPageResponse;
import kr.ktb.zura.needu.product.service.ProductRecommendationService;
import kr.ktb.zura.needu.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

// AI 서버 호출과 DB 저장을 서로 다른 트랜잭션으로 나눠야 함 => 이 클래스는 트랜잭션을 시작하지 않음
@Slf4j
@Component
public class AiChatFacade {

    private final AiChatRoomService aiChatRoomService;
    private final AiMessageService aiMessageService;
    private final AiConversationLock aiConversationLock;
    private final AiChatClient aiChatClient;
    private final Duration messageRetryAfter;
    private final ProductRecommendationService productRecommendationService;
    private final UserService userService;

    public AiChatFacade(AiChatRoomService aiChatRoomService,
                        AiMessageService aiMessageService,
                        AiConversationLock aiConversationLock,
                        AiChatClient aiChatClient,
                        ProductRecommendationService productRecommendationService,
                        UserService userService,
                        @Value("${ai.chat.message-retry-after:10s}") Duration messageRetryAfter) {
        this.aiChatRoomService = aiChatRoomService;
        this.aiMessageService = aiMessageService;
        this.aiConversationLock = aiConversationLock;
        this.aiChatClient = aiChatClient;
        this.messageRetryAfter = messageRetryAfter;
        this.productRecommendationService = productRecommendationService;
        this.userService = userService;
    }

    public AiConversationStartResult startOrResumeConversation(Long userId) {
        // TODO: AI 정보 활용 동의 여부 확인(403) — 동의 저장 방식 확정 후 구현 (V2)
        AiChatRoom room = aiChatRoomService.findOrReserveRoom(userId);
        if (room.isPending()) {
            return startConversation(userId, room.getId());
        }
        // AI 서버에 세션 조회 API가 없어 purgeAt으로 만료를 판단, 규칙보다 일찍 사라진 세션은 메시지 전송·분석 시 발견
        // ANALYZING도 AI 세션이 만료되면 분석을 이어갈 수 없음 => 다른 대화 API처럼 만료로 보고 새 대화를 시작한다
        if (room.isExpiredAt(LocalDateTime.now(ZoneOffset.UTC))) {
            return restartConversation(userId, room.getId());
        }
        return AiConversationStartResult.resumed(
                AiConversationResponse.from(room, aiMessageService.findLatestProgress(room.getId())));
    }

    public CursorPageResponse<AiMessageSummaryResponse> findAllMessages(
            Long userId, Long conversationId, String cursor, int size) {
        aiChatRoomService.validateActiveRoom(userId, conversationId);
        return aiMessageService.findAllMessages(conversationId, cursor, size);
    }

    public AiMessageResponse sendMessage(Long userId, Long conversationId, SendMessageRequest request) {
        // TODO: AI 정보 활용 동의 여부 확인(403) — 동의 저장 방식 확정 후 구현 (V2)
        aiChatRoomService.validateMessageSendableRoom(userId, conversationId);
        if (!aiConversationLock.tryLock(conversationId)) {
            log.info("AI message already in progress. userId={}, conversationId={}", userId, conversationId);
            throw new TooManyRequestsException(messageRetryAfter.toSeconds());
        }
        try {
            return replyToUserMessage(userId, conversationId, request);
        } finally {
            aiConversationLock.unlock(conversationId);
        }
    }

    private AiMessageResponse replyToUserMessage(Long userId, Long conversationId, SendMessageRequest request) {
        Optional<AiMessage> sentMessage = aiMessageService.findUserMessage(conversationId, request.clientMessageId());
        if (sentMessage.isPresent()) {
            return findSentReply(sentMessage.get());
        }
        AiMessage userMessage = createUserMessage(conversationId, request);
        AiServerSendMessageResponse response = requestReply(userId, conversationId, request.content());
        AiMessage aiMessage = aiMessageService.createReplyAndUpdateRoom(
                conversationId, userMessage.getId(), response.reply(), response.progress(),
                response.inputLocked(), toPurgeAt(response.expirationAt()));
        log.info("AI message replied. userId={}, conversationId={}, messageId={}",
                userId, conversationId, aiMessage.getId());
        return AiMessageResponse.from(aiMessage);
    }

    // 같은 clientMessageId로 다시 들어온 요청 => AI를 다시 호출하지 않고 이미 저장한 답변을 그대로 반환한다
    private AiMessageResponse findSentReply(AiMessage userMessage) {
        return aiMessageService.findReply(userMessage.getId())
                .map(AiMessageResponse::from)
                .orElseThrow(() -> new TooManyRequestsException(messageRetryAfter.toSeconds()));
    }

    private AiMessage createUserMessage(Long conversationId, SendMessageRequest request) {
        try {
            return aiMessageService.createUserMessage(conversationId, request.clientMessageId(), request.content());
        } catch (DataIntegrityViolationException e) {
            // 다른 인스턴스가 같은 clientMessageId를 이미 처리 중 => 대화방 락이 막지 못하는 경우
            throw new TooManyRequestsException(messageRetryAfter.toSeconds());
        }
    }

    private AiServerSendMessageResponse requestReply(Long userId, Long conversationId, String content) {
        try {
            AiServerSendMessageResponse response = aiChatClient.sendMessage(userId, conversationId, content);
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
        aiChatRoomService.validateAnalyzableRoom(userId, conversationId);
        try {
            return toAnalysisResult(aiChatClient.createAnalysis(conversationId));
        } catch (BusinessException e) {
            expireRoomIfSessionGone(userId, conversationId, e);
            throw e;
        }
    }

    public AnalysisResultResponse patchAnalyze(Long userId, Long conversationId, PatchAnalyzeMessageRequest request) {
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
        aiChatRoomService.validateActiveRoom(userId, conversationId);
        aiChatRoomService.startAnalysis(conversationId);
        AiServerCloseSessionResponse response;
        try {
            response = aiChatClient.confirmAnalysis(userId, conversationId);
        } catch (BusinessException e) {
            expireRoomIfSessionGone(userId, conversationId, e);
            throw e;
        }
        validateCloseResponse(response);
        productRecommendationService.saveRecommendations(
                userId,
                response.recommendations().self(),
                response.recommendations().gift(),
                response.keywords().taste());
        userService.completeTasteAnalysis(
                userId, response.summary(), response.keywords().taste(), response.keywords().interest());
        aiChatRoomService.completeRoom(conversationId);
        return ProductRecommendationStatusResponse.success();
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

    private boolean hasInvalidFinalKeywords(List<String> keywords) {
        return keywords == null
                || keywords.size() > 3
                || keywords.stream().anyMatch(keyword -> keyword == null || keyword.isBlank());
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
