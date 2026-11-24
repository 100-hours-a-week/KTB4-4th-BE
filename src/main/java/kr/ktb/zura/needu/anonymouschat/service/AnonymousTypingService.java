package kr.ktb.zura.needu.anonymouschat.service;

import kr.ktb.zura.needu.anonymouschat.entity.AnonymousRoom;
import kr.ktb.zura.needu.anonymouschat.ws.AnonymousChatEvent;
import kr.ktb.zura.needu.anonymouschat.ws.AnonymousMessageRedisEvent;
import kr.ktb.zura.needu.anonymouschat.ws.AnonymousTypingEvent;
import kr.ktb.zura.needu.common.ratelimit.EventRateLimiter;
import kr.ktb.zura.needu.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

// 저장할 데이터가 없어 트랜잭션 없이 검증 후 바로 발행
@Slf4j
@Service
@RequiredArgsConstructor
public class AnonymousTypingService {

    private static final String TYPING_RATE_LIMIT_EVENT = "anonymous-chat-typing";

    private final AnonymousChatService anonymousChatService;
    private final UserService userService;
    private final EventRateLimiter eventRateLimiter;
    private final AnonymousMessageEventPublisher anonymousMessageEventPublisher;
    private final JsonMapper jsonMapper;

    public void updateTyping(Long userId, Long roomId, boolean isTyping) {
        eventRateLimiter.validateWithinLimit(TYPING_RATE_LIMIT_EVENT, userId);
        userService.validateActiveUser(userId);
        AnonymousRoom room = anonymousChatService.findParticipatingRoom(userId, roomId);
        if (!room.isActive() || !room.isPartnerJoined(userId)) {
            return;
        }

        publish(roomId, room.getPartnerUserId(userId), isTyping);
    }

    private void publish(Long roomId, Long receiverUserId, boolean isTyping) {
        AnonymousChatEvent chatEvent = AnonymousChatEvent.typing(new AnonymousTypingEvent(roomId, isTyping));
        try {
            anonymousMessageEventPublisher.publish(
                    new AnonymousMessageRedisEvent(receiverUserId, jsonMapper.valueToTree(chatEvent)));
        } catch (RuntimeException exception) {
            log.warn("Anonymous typing Redis publish failed. roomId={}, receiverUserId={}",
                    roomId, receiverUserId, exception);
        }
    }
}
