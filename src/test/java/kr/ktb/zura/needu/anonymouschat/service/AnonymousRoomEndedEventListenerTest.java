package kr.ktb.zura.needu.anonymouschat.service;

import java.time.OffsetDateTime;
import kr.ktb.zura.needu.anonymouschat.type.AnonymousRoomEndedReason;
import kr.ktb.zura.needu.anonymouschat.type.AnonymousRoomStatus;
import kr.ktb.zura.needu.anonymouschat.ws.AnonymousMessageRedisEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AnonymousRoomEndedEventListenerTest {

    private static final Long RECEIVER_USER_ID = 200L;
    private static final Long ROOM_ID = 1L;
    private static final OffsetDateTime ENDED_AT = OffsetDateTime.parse("2026-10-28T15:30:00+09:00");

    private final AnonymousMessageEventPublisher eventPublisher = mock(AnonymousMessageEventPublisher.class);
    private final AnonymousRoomEndedEventListener listener = new AnonymousRoomEndedEventListener(
            eventPublisher, JsonMapper.builder().build());

    @Test
    void partnerLeft_publish_sendsRoomEndedEventWithPartnerLeftReason() {
        listener.publish(new AnonymousRoomEndedEvent(
                RECEIVER_USER_ID, ROOM_ID, AnonymousRoomStatus.EXPIRED, AnonymousRoomEndedReason.PARTNER_LEFT, ENDED_AT));

        JsonNode event = capturePublishedEvent();
        assertThat(event.get("type").asString()).isEqualTo("ANONYMOUS_CHAT_ROOM_ENDED");
        assertThat(event.get("data").get("roomId").asLong()).isEqualTo(ROOM_ID);
        assertThat(event.get("data").get("roomStatus").asString()).isEqualTo("EXPIRED");
        assertThat(event.get("data").get("endedReason").asString()).isEqualTo("PARTNER_LEFT");
        assertThat(event.get("data").get("endedAt").asString()).isEqualTo("2026-10-28T15:30:00+09:00");
    }

    @Test
    void userBlocked_publish_sendsRoomEndedEventWithUserBlockedReason() {
        listener.publish(new AnonymousRoomEndedEvent(
                RECEIVER_USER_ID, ROOM_ID, AnonymousRoomStatus.ENDED, AnonymousRoomEndedReason.USER_BLOCKED, ENDED_AT));

        JsonNode event = capturePublishedEvent();
        assertThat(event.get("type").asString()).isEqualTo("ANONYMOUS_CHAT_ROOM_ENDED");
        assertThat(event.get("data").get("roomStatus").asString()).isEqualTo("ENDED");
        assertThat(event.get("data").get("endedReason").asString()).isEqualTo("USER_BLOCKED");
    }

    @Test
    void redisFailure_publish_doesNotFailCommittedRequest() {
        willThrow(new IllegalStateException("Redis unavailable"))
                .given(eventPublisher).publish(any(AnonymousMessageRedisEvent.class));

        assertThatCode(() -> listener.publish(new AnonymousRoomEndedEvent(
                RECEIVER_USER_ID, ROOM_ID, AnonymousRoomStatus.ENDED, AnonymousRoomEndedReason.USER_BLOCKED, ENDED_AT)))
                .doesNotThrowAnyException();
    }

    private JsonNode capturePublishedEvent() {
        ArgumentCaptor<AnonymousMessageRedisEvent> eventCaptor =
                ArgumentCaptor.forClass(AnonymousMessageRedisEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().receiverUserId()).isEqualTo(RECEIVER_USER_ID);
        return eventCaptor.getValue().event();
    }
}
