package kr.ktb.zura.needu.anonymouschat.service;

import java.time.ZoneOffset;
import kr.ktb.zura.needu.anonymouschat.ws.AnonymousChatEvent;
import kr.ktb.zura.needu.anonymouschat.ws.AnonymousMessageReadEvent;
import kr.ktb.zura.needu.anonymouschat.ws.AnonymousMessageRedisEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnonymousReadPositionUpdatedEventListener {

    private final AnonymousMessageEventPublisher anonymousMessageEventPublisher;
    private final JsonMapper jsonMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(AnonymousReadPositionUpdatedEvent event) {
        AnonymousChatEvent chatEvent = AnonymousChatEvent.messageRead(new AnonymousMessageReadEvent(
                event.roomId(), event.lastReadMessageId(), event.readAt().atOffset(ZoneOffset.UTC)));
        try {
            anonymousMessageEventPublisher.publish(
                    new AnonymousMessageRedisEvent(event.receiverUserId(), jsonMapper.valueToTree(chatEvent)));
        } catch (RuntimeException exception) {
            log.warn("Anonymous message read Redis publish failed. roomId={}, lastReadMessageId={}, receiverUserId={}",
                    event.roomId(), event.lastReadMessageId(), event.receiverUserId(), exception);
        }
    }
}
