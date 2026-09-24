package kr.ktb.zura.needu.aichat.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

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

    public AiChatRoomService(AiChatRoomRepository aiChatRoomRepository,
                             AiMessageRepository aiMessageRepository,
                             @Value("${ai.chat.pending-timeout:1m}") Duration pendingTimeout) {
        this.aiChatRoomRepository = aiChatRoomRepository;
        this.aiMessageRepository = aiMessageRepository;
        this.pendingTimeout = pendingTimeout;
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
        if (room.isReservedBefore(LocalDateTime.now(ZoneOffset.UTC).minus(pendingTimeout))) {
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
    public AiChatRoom activateRoom(Long roomId, String greeting, LocalDateTime purgeAt) {
        AiChatRoom room = findRoom(roomId);
        room.activate(purgeAt);
        aiMessageRepository.save(AiMessage.createGreeting(room, greeting));
        return room;
    }

    // 대화 작업이 가능한 상태(소유자 본인, ACTIVE/ANALYZING, 만료 전)인지 확인한다
    @Transactional(readOnly = true)
    public void validateActiveRoom(Long userId, Long roomId) {
        findAccessibleRoom(userId, roomId);
    }

    @Transactional(readOnly = true)
    public void validateMessageSendableRoom(Long userId, Long roomId) {
        if (findAccessibleRoom(userId, roomId).isInputLocked()) {
            throw new BusinessException(AiChatErrorCode.AICHAT_INPUT_LOCKED);
        }
    }

    @Transactional(readOnly = true)
    public void validateAnalyzableRoom(Long userId, Long roomId) {
        AiChatRoom room = findAccessibleRoom(userId, roomId);
        if (!room.isAnalyzing() || !room.isInputLocked()) {
            throw new BusinessException(AiChatErrorCode.AICHAT_ANALYSIS_NOT_READY);
        }
    }

    private AiChatRoom findAccessibleRoom(Long userId, Long roomId) {
        AiChatRoom room = aiChatRoomRepository.findById(roomId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BusinessException(AiChatErrorCode.AICHAT_CONVERSATION_NOT_FOUND));
        // 명세상 남의 대화는 403, 끝났거나 만료된 대화는 404로 구분해야 해 소유자 조건을 쿼리에 넣지 않는다
        if (!room.isOwnedBy(userId)) {
            throw new BusinessException(AiChatErrorCode.AICHAT_CONVERSATION_FORBIDDEN);
        }
        if ((!room.isActive() && !room.isAnalyzing())
                || room.isExpiredAt(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new BusinessException(AiChatErrorCode.AICHAT_CONVERSATION_NOT_FOUND);
        }
        return room;
    }

    @Transactional
    public void expireRoom(Long roomId) {
        findRoom(roomId).expire();
    }

    @Transactional
    public void completeRoom(Long roomId) {
        findRoom(roomId).complete();
    }

    @Transactional
    public void startAnalysis(Long roomId) {
        findRoom(roomId).startAnalysis();
    }

    @Transactional
    public void discardRoom(Long roomId) {
        findRoom(roomId).discard(LocalDateTime.now(ZoneOffset.UTC));
    }

    private AiChatRoom findRoom(Long roomId) {
        return aiChatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalStateException("AI chat room not found. conversationId=" + roomId));
    }

    private void discardAndFlush(AiChatRoom room) {
        room.discard(LocalDateTime.now(ZoneOffset.UTC));
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
