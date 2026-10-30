package kr.ktb.zura.needu.anonymouschat.service;

import java.time.OffsetDateTime;
import kr.ktb.zura.needu.anonymouschat.type.AnonymousRoomParticipantStatus;
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
public class AnonymousRoomLeftEventListener {

    private static final String EVENT_TYPE = "ANONYMOUS_CHAT_PARTNER_LEFT";

    private final AnonymousMessageEventPublisher eventPublisher;
    private final JsonMapper jsonMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(AnonymousRoomLeftEvent event) {
        try {
            eventPublisher.publish(new AnonymousMessageRedisEvent(
                    event.receiverUserId(),
                    jsonMapper.valueToTree(new EventPayload(
                            EVENT_TYPE,
                            new PartnerLeftData(
                                    event.roomId(),
                                    event.roomStatus(),
                                    AnonymousRoomParticipantStatus.LEFT,
                                    event.leftAt()
                            )
                    ))
            ));
        } catch (RuntimeException exception) {
            log.warn("Anonymous room left event publish failed. roomId={}, receiverUserId={}",
                    event.roomId(), event.receiverUserId(), exception);
        }
    }

    private record EventPayload(String type, PartnerLeftData data) {
    }

    private record PartnerLeftData(
            Long roomId,
            AnonymousRoomStatus roomStatus,
            AnonymousRoomParticipantStatus partnerStatus,
            OffsetDateTime leftAt
    ) {
    }
}
