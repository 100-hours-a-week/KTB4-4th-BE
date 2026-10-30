package kr.ktb.zura.needu.anonymouschat.service;

import java.time.OffsetDateTime;
import kr.ktb.zura.needu.anonymouschat.type.AnonymousRoomStatus;
import kr.ktb.zura.needu.anonymouschat.ws.AnonymousMessageRedisEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AnonymousRoomLeftEventListenerTest {

    private static final Long RECEIVER_USER_ID = 200L;
    private static final Long ROOM_ID = 1L;
    private static final OffsetDateTime LEFT_AT = OffsetDateTime.parse("2026-10-29T15:30:00+09:00");

    private final AnonymousMessageEventPublisher eventPublisher = mock(AnonymousMessageEventPublisher.class);
    private final AnonymousRoomLeftEventListener listener = new AnonymousRoomLeftEventListener(
            eventPublisher, JsonMapper.builder().build());

    @Test
    void partnerLeft_publish_sendsPartnerLeftEvent() {
        listener.publish(new AnonymousRoomLeftEvent(RECEIVER_USER_ID, ROOM_ID, AnonymousRoomStatus.EXPIRED, LEFT_AT));

        ArgumentCaptor<AnonymousMessageRedisEvent> eventCaptor =
                ArgumentCaptor.forClass(AnonymousMessageRedisEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());
        AnonymousMessageRedisEvent event = eventCaptor.getValue();
        assertThat(event.receiverUserId()).isEqualTo(RECEIVER_USER_ID);
        assertThat(event.event().get("type").asString()).isEqualTo("ANONYMOUS_CHAT_PARTNER_LEFT");
        assertThat(event.event().get("data").get("roomId").asLong()).isEqualTo(ROOM_ID);
        assertThat(event.event().get("data").get("roomStatus").asString()).isEqualTo("EXPIRED");
        assertThat(event.event().get("data").get("partnerStatus").asString()).isEqualTo("LEFT");
        assertThat(event.event().get("data").get("leftAt").asString()).isEqualTo("2026-10-29T15:30:00+09:00");
    }

    @Test
    void redisFailure_publish_doesNotFailCommittedRequest() {
        willThrow(new IllegalStateException("Redis unavailable"))
                .given(eventPublisher).publish(any(AnonymousMessageRedisEvent.class));

        assertThatCode(() -> listener.publish(
                new AnonymousRoomLeftEvent(RECEIVER_USER_ID, ROOM_ID, AnonymousRoomStatus.EXPIRED, LEFT_AT)))
                .doesNotThrowAnyException();
    }
}
