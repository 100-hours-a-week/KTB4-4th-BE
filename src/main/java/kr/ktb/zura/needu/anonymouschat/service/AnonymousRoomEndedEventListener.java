package kr.ktb.zura.needu.anonymouschat.service;

import java.time.OffsetDateTime;
import kr.ktb.zura.needu.anonymouschat.type.AnonymousRoomEndedReason;
import kr.ktb.zura.needu.anonymouschat.type.AnonymousRoomStatus;
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
public class AnonymousRoomEndedEventListener {

    private static final String EVENT_TYPE = "ANONYMOUS_CHAT_ROOM_ENDED";

    private final AnonymousMessageEventPublisher eventPublisher;
    private final JsonMapper jsonMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(AnonymousRoomEndedEvent event) {
        try {
            eventPublisher.publish(new AnonymousMessageRedisEvent(
                    event.receiverUserId(),
                    jsonMapper.valueToTree(new EventPayload(
                            EVENT_TYPE,
                            new RoomEndedData(
                                    event.roomId(),
                                    AnonymousRoomStatus.ENDED,
                                    AnonymousRoomEndedReason.PARTNER_LEFT,
                                    event.endedAt()
                            )
                    ))
            ));
        } catch (RuntimeException exception) {
            log.warn("Anonymous room ended event publish failed. roomId={}, receiverUserId={}",
                    event.roomId(), event.receiverUserId(), exception);
        }
    }

    private record EventPayload(String type, RoomEndedData data) {
    }

    private record RoomEndedData(
            Long roomId,
            AnonymousRoomStatus roomStatus,
            AnonymousRoomEndedReason endedReason,
            OffsetDateTime endedAt
    ) {
    }
}
