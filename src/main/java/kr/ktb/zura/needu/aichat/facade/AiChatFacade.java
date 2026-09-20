package kr.ktb.zura.needu.aichat.facade;

import java.time.LocalDateTime;

import kr.ktb.zura.needu.aichat.client.AiChatClient;
import kr.ktb.zura.needu.aichat.dto.response.AiConversationResponse;
import kr.ktb.zura.needu.aichat.dto.response.AiServerStartSessionResponse;
import kr.ktb.zura.needu.aichat.entity.AiChatRoom;
import kr.ktb.zura.needu.aichat.exception.AiChatErrorCode;
import kr.ktb.zura.needu.aichat.service.AiChatRoomService;
import kr.ktb.zura.needu.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// AI 서버 호출과 DB 저장을 서로 다른 트랜잭션으로 나눠야 함 => 이 클래스는 트랜잭션을 시작하지 않음
@Slf4j
@Component
@RequiredArgsConstructor
public class AiChatFacade {

    private final AiChatRoomService aiChatRoomService;
    private final AiChatClient aiChatClient;

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
}
