package kr.ktb.zura.needu.aichat.service;

import java.time.Duration;
import java.time.LocalDateTime;

import kr.ktb.zura.needu.aichat.entity.AiChatRoom;
import kr.ktb.zura.needu.aichat.entity.AiMessage;
import kr.ktb.zura.needu.aichat.exception.AiChatErrorCode;
import kr.ktb.zura.needu.aichat.repository.AiChatRoomRepository;
import kr.ktb.zura.needu.aichat.repository.AiMessageRepository;
import kr.ktb.zura.needu.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// AI 호출 동안 트랜잭션을 유지하지 않도록 대화방 상태 변경만 짧은 트랜잭션으로 나눠 담당
@Slf4j
@Service
public class AiChatRoomService {

    private final AiChatRoomRepository aiChatRoomRepository;
    private final AiMessageRepository aiMessageRepository;
    private final Duration pendingTimeout;
    private final Duration sessionIdleTimeout;
    private final Duration sessionMaxDuration;
    private final Duration sessionExpiryMargin;

    public AiChatRoomService(AiChatRoomRepository aiChatRoomRepository,
                             AiMessageRepository aiMessageRepository,
                             @Value("${ai.chat.pending-timeout:1m}") Duration pendingTimeout,
                             @Value("${ai.chat.session-idle-timeout:30m}") Duration sessionIdleTimeout,
                             @Value("${ai.chat.session-max-duration:2h}") Duration sessionMaxDuration,
                             @Value("${ai.chat.session-expiry-margin:1m}") Duration sessionExpiryMargin) {
        this.aiChatRoomRepository = aiChatRoomRepository;
        this.aiMessageRepository = aiMessageRepository;
        this.pendingTimeout = pendingTimeout;
        this.sessionIdleTimeout = sessionIdleTimeout;
        this.sessionMaxDuration = sessionMaxDuration;
        this.sessionExpiryMargin = sessionExpiryMargin;
    }

    // 반환된 방이 PENDING => 이번 호출에서 새로 예약한 방
    @Transactional
    public AiChatRoom findOrReserveRoom(Long userId) {
        AiChatRoom room = aiChatRoomRepository.findByActiveUserId(userId).orElse(null);
        if (room == null) {
            return reserveRoom(userId);
        }
        if (!room.isPending()) {
            return room;
        }
        // AI 호출 제한 시간보다 오래 PENDING => 이전 요청이 비정상 종료된 것으로 보고 정리
        if (room.isReservedBefore(LocalDateTime.now().minus(pendingTimeout))) {
            log.info("Stale AI chat room discarded. userId={}, conversationId={}", userId, room.getId());
            discardAndFlush(room);
            return reserveRoom(userId);
        }
        throw new BusinessException(AiChatErrorCode.AICHAT_CONVERSATION_STARTING);
    }

    @Transactional
    public AiChatRoom expireAndReserveRoom(Long userId, Long expiredRoomId) {
        AiChatRoom expiredRoom = findRoom(expiredRoomId);
        expiredRoom.expire();
        // Hibernate -> INSERT를 UPDATE보다 먼저 실행, 유일 제약 충돌을 막기 위해 만료 처리 우선 반영
        aiChatRoomRepository.flush();
        return reserveRoom(userId);
    }

    @Transactional
    public AiChatRoom activateRoom(Long roomId, String greeting) {
        AiChatRoom room = findRoom(roomId);
        room.activate(calculatePurgeAt(room, LocalDateTime.now()));
        aiMessageRepository.save(AiMessage.createGreeting(room, greeting));
        return room;
    }

    // 메시지를 보낼 수 있는 상태(소유자 본인, ACTIVE, 만료 전)인지 확인한다
    @Transactional(readOnly = true)
    public AiChatRoom findActiveRoom(Long userId, Long roomId) {
        AiChatRoom room = aiChatRoomRepository.findById(roomId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BusinessException(AiChatErrorCode.AICHAT_CONVERSATION_NOT_FOUND));
        // 명세상 남의 대화는 403, 끝났거나 만료된 대화는 404로 구분해야 해 소유자 조건을 쿼리에 넣지 않는다
        if (!room.isOwnedBy(userId)) {
            throw new BusinessException(AiChatErrorCode.AICHAT_CONVERSATION_FORBIDDEN);
        }
        if (!room.isActive() || room.isExpiredAt(LocalDateTime.now())) {
            throw new BusinessException(AiChatErrorCode.AICHAT_CONVERSATION_NOT_FOUND);
        }
        return room;
    }

    @Transactional
    public void expireRoom(Long roomId) {
        findRoom(roomId).expire();
    }

    // 마지막 활동 시각이 갱신됐으므로 만료 시각을 다시 계산한다
    @Transactional
    public void extendSession(Long roomId) {
        AiChatRoom room = findRoom(roomId);
        room.extendPurgeAt(calculatePurgeAt(room, LocalDateTime.now()));
    }

    @Transactional
    public void discardRoom(Long roomId) {
        findRoom(roomId).discard(LocalDateTime.now());
    }

    // AI 명세의 만료 규칙(마지막 활동 후 30분, 시작 후 최대 2시간)으로 계산
    // BE의 시각은 AI 서버의 세션 생성/갱신 시각보다 늦으므로 여유 시간을 빼 AI 세션보다 먼저 만료되게
    private LocalDateTime calculatePurgeAt(AiChatRoom room, LocalDateTime lastActivityAt) {
        LocalDateTime idleExpiresAt = lastActivityAt.plus(sessionIdleTimeout);
        LocalDateTime maxExpiresAt = room.getCreatedAt().plus(sessionMaxDuration);
        LocalDateTime expiresAt = idleExpiresAt.isBefore(maxExpiresAt) ? idleExpiresAt : maxExpiresAt;
        return expiresAt.minus(sessionExpiryMargin);
    }

    private AiChatRoom findRoom(Long roomId) {
        return aiChatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalStateException("AI chat room not found. conversationId=" + roomId));
    }

    private void discardAndFlush(AiChatRoom room) {
        room.discard(LocalDateTime.now());
        aiChatRoomRepository.flush();
    }

    // 동시에 들어온 요청이 먼저 방을 예약했다면 유일 제약 위반이 발생 => 대화 시작 중으로 응답
    private AiChatRoom reserveRoom(Long userId) {
        try {
            return aiChatRoomRepository.saveAndFlush(AiChatRoom.reserve(userId));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(AiChatErrorCode.AICHAT_CONVERSATION_STARTING);
        }
    }
}
