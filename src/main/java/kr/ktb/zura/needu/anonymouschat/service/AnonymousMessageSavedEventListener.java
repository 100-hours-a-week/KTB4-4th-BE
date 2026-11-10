package kr.ktb.zura.needu.anonymouschat.service;

import java.time.OffsetDateTime;
import kr.ktb.zura.needu.anonymouschat.support.AnonymousChatTime;
import kr.ktb.zura.needu.anonymouschat.type.AnonymousMessageSender;
import kr.ktb.zura.needu.anonymouschat.ws.AnonymousChatEvent;
import kr.ktb.zura.needu.anonymouschat.ws.AnonymousMessageReceivedEvent;
import kr.ktb.zura.needu.anonymouschat.ws.AnonymousMessageRedisEvent;
import kr.ktb.zura.needu.anonymouschat.ws.AnonymousMessageSentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

// 커밋된 메시지만 전달되도록 커밋 이후에 발행
@Slf4j
@Component
@RequiredArgsConstructor
public class AnonymousMessageSavedEventListener {

    private final AnonymousMessageEventPublisher anonymousMessageEventPublisher;
    private final JsonMapper jsonMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(AnonymousMessageSavedEvent event) {
        OffsetDateTime sentAt = AnonymousChatTime.toKst(event.sentAt());
        publish(event, event.senderUserId(), AnonymousChatEvent.messageSent(
                new AnonymousMessageSentEvent(
                        event.roomId(), event.clientMessageId(), event.messageId(), event.content(), sentAt)
        ));
        if (event.duplicated()) {
            return;
        }
        publish(event, event.partnerUserId(), AnonymousChatEvent.messageReceived(
                new AnonymousMessageReceivedEvent(
                        event.roomId(), event.messageId(), AnonymousMessageSender.OTHER, event.content(), sentAt)
        ));
    }

    private void publish(AnonymousMessageSavedEvent event, Long receiverUserId, AnonymousChatEvent chatEvent) {
        try {
            anonymousMessageEventPublisher.publish(
                    new AnonymousMessageRedisEvent(receiverUserId, jsonMapper.valueToTree(chatEvent)));
        } catch (RuntimeException exception) {
            log.warn("Anonymous message Redis publish failed. roomId={}, messageId={}, receiverUserId={}, type={}",
                    event.roomId(), event.messageId(), receiverUserId, chatEvent.type(), exception);
        }
    }
}
