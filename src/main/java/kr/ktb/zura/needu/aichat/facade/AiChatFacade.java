package kr.ktb.zura.needu.aichat.facade;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import kr.ktb.zura.needu.aichat.client.AiChatClient;
import kr.ktb.zura.needu.aichat.dto.request.SendMessageRequest;
import kr.ktb.zura.needu.aichat.dto.response.AiConversationResponse;
import kr.ktb.zura.needu.aichat.dto.response.AiMessageResponse;
import kr.ktb.zura.needu.aichat.dto.response.AiMessageSummaryResponse;
import kr.ktb.zura.needu.aichat.dto.response.AiServerAnalysisResponse;
import kr.ktb.zura.needu.aichat.dto.response.AiServerSendMessageResponse;
import kr.ktb.zura.needu.aichat.dto.response.AiServerStartSessionResponse;
import kr.ktb.zura.needu.aichat.dto.response.AnalysisResultResponse;
import kr.ktb.zura.needu.aichat.entity.AiChatRoom;
import kr.ktb.zura.needu.aichat.entity.AiMessage;
import kr.ktb.zura.needu.aichat.exception.AiChatErrorCode;
import kr.ktb.zura.needu.aichat.service.AiChatRoomService;
import kr.ktb.zura.needu.aichat.service.AiConversationLock;
import kr.ktb.zura.needu.aichat.service.AiMessageService;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.TooManyRequestsException;
import kr.ktb.zura.needu.common.response.CursorPageResponse;
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

    public AiChatFacade(AiChatRoomService aiChatRoomService,
                        AiMessageService aiMessageService,
                        AiConversationLock aiConversationLock,
                        AiChatClient aiChatClient,
                        @Value("${ai.chat.message-retry-after:10s}") Duration messageRetryAfter) {
        this.aiChatRoomService = aiChatRoomService;
        this.aiMessageService = aiMessageService;
        this.aiConversationLock = aiConversationLock;
        this.aiChatClient = aiChatClient;
        this.messageRetryAfter = messageRetryAfter;
    }

    public AiConversationStartResult startOrResumeConversation(Long userId) {
        // TODO: AI 정보 활용 동의 여부 확인(403) — 동의 저장 방식 확정 후 구현 (V2)
        AiChatRoom room = aiChatRoomService.findOrReserveRoom(userId);
        if (room.isPending()) {
            return startConversation(userId, room.getId());
        }
        // AI 서버에 세션 조회 API가 없어 purgeAt으로 만료를 판단, 규칙보다 일찍 사라진 세션은 메시지 전송 시 발견
        if (room.isActive() && room.isExpiredAt(LocalDateTime.now())) {
            return restartConversation(userId, room.getId());
        }
        return AiConversationStartResult.resumed(AiConversationResponse.from(room));
    }

    public CursorPageResponse<AiMessageSummaryResponse> findAllMessages(
            Long userId, Long conversationId, String cursor, int size) {
        aiChatRoomService.findActiveRoom(userId, conversationId);
        return aiMessageService.findAllMessages(conversationId, cursor, size);
    }

    public AiMessageResponse sendMessage(Long userId, Long conversationId, SendMessageRequest request) {
        // TODO: AI 정보 활용 동의 여부 확인(403) — 동의 저장 방식 확정 후 구현 (V2)
        aiChatRoomService.findActiveRoom(userId, conversationId);
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
        String reply = requestReply(userId, conversationId, request.content());
        AiMessage aiMessage = aiMessageService.createReply(conversationId, userMessage.getId(), reply);
        aiChatRoomService.extendSession(conversationId);
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

    private String requestReply(Long userId, Long conversationId, String content) {
        try {
            return toReply(aiChatClient.sendMessage(conversationId, content));
        } catch (BusinessException e) {
            if (!(e.getErrorCode() instanceof AiChatErrorCode errorCode)) {
                throw e;
            }
            if (errorCode.isSessionGone()) {
                // AI 세션이 만료 규칙보다 일찍 사라졌거나 이미 닫힘 => 방을 정리해 새 대화를 시작하게 한다
                log.info("AI chat session gone. userId={}, conversationId={}, code={}",
                        userId, conversationId, errorCode.name());
                aiChatRoomService.expireRoom(conversationId);
                throw e;
            }
            if (errorCode == AiChatErrorCode.AICHAT_TURN_IN_PROGRESS) {
                // AI 서버가 이전 메시지를 처리 중 => 대화방을 그대로 두고 재시도 시각만 알려준다
                log.info("AI turn in progress. userId={}, conversationId={}", userId, conversationId);
                throw new TooManyRequestsException(messageRetryAfter.toSeconds());
            }
            throw e;
        }
    }

    private String toReply(AiServerSendMessageResponse response) {
        if (response.reply() == null || response.reply().isBlank()) {
            throw new BusinessException(AiChatErrorCode.AICHAT_INVALID_RESPONSE);
        }
        return response.reply();
    }

    private AiConversationStartResult restartConversation(Long userId, Long expiredRoomId) {
        log.info("AI chat session expired. userId={}, conversationId={}", userId, expiredRoomId);
        AiChatRoom newRoom = aiChatRoomService.expireAndReserveRoom(userId, expiredRoomId);
        return startConversation(userId, newRoom.getId());
    }

    private AiConversationStartResult startConversation(Long userId, Long roomId) {
        try {
            AiServerStartSessionResponse session = aiChatClient.startSession(userId, roomId);
            AiChatRoom room = aiChatRoomService.activateRoom(roomId, toGreeting(session));
            log.info("AI conversation started. userId={}, conversationId={}", userId, roomId);
            return AiConversationStartResult.created(AiConversationResponse.from(room));
        } catch (RuntimeException e) {
            // 실패한 방이 PENDING으로 남으면 재시도가 막히므로 즉시 정리
            aiChatRoomService.discardRoom(roomId);
            throw e;
        }
    }

    private String toGreeting(AiServerStartSessionResponse session) {
        if (session.greeting() == null || session.greeting().isBlank()) {
            throw new BusinessException(AiChatErrorCode.AICHAT_INVALID_RESPONSE);
        }
        return session.greeting();
    }

    public AnalysisResultResponse createAnalysis(Long userId, Long conversationId) {
        aiChatRoomService.findActiveRoom(userId, conversationId);
        return toAnalysisResult(aiChatClient.createAnalysis(conversationId));
    }

    private AnalysisResultResponse toAnalysisResult(AiServerAnalysisResponse response) {
        if (response.summary() == null || response.summary().isBlank()
                || response.keywords() == null
                || response.keywords().taste() == null
                || response.keywords().interest() == null
                || response.correctionAvailable() == null) {
            throw new BusinessException(AiChatErrorCode.AICHAT_INVALID_RESPONSE);
        }
        return AnalysisResultResponse.from(response);
    }
}
