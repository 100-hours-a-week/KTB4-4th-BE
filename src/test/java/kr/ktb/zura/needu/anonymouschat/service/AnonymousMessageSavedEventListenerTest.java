package kr.ktb.zura.needu.anonymouschat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import kr.ktb.zura.needu.anonymouschat.ws.AnonymousMessageRedisEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class AnonymousMessageSavedEventListenerTest {

    private static final Long ROOM_ID = 1L;
    private static final Long SENDER_ID = 100L;
    private static final Long PARTNER_ID = 200L;
    private static final Long MESSAGE_ID = 501L;
    private static final UUID CLIENT_MESSAGE_ID = UUID.fromString("7f6c1f0e-0000-4000-8000-000000000001");

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final AnonymousMessageEventPublisher publisher = mock(AnonymousMessageEventPublisher.class);
    private final AnonymousMessageSavedEventListener listener =
            new AnonymousMessageSavedEventListener(publisher, jsonMapper);

    @Test
    void newMessage_publish_sendsSentToSenderAndReceivedToPartner() {
        listener.publish(savedEvent(false));

        List<AnonymousMessageRedisEvent> events = capturePublishedEvents(2);
        AnonymousMessageRedisEvent sent = events.get(0);
        assertThat(sent.receiverUserId()).isEqualTo(SENDER_ID);
        assertThat(sent.event().toString()).isEqualTo(jsonMapper.readTree("""
                {"type":"ANONYMOUS_CHAT_MESSAGE_SENT",
                 "data":{"roomId":1,"clientMessageId":"7f6c1f0e-0000-4000-8000-000000000001",
                         "messageId":501,"content":"안녕하세요","sentAt":"2026-10-28T01:00:00Z"}}
                """).toString());

        AnonymousMessageRedisEvent received = events.get(1);
        assertThat(received.receiverUserId()).isEqualTo(PARTNER_ID);
        assertThat(received.event().toString()).isEqualTo(jsonMapper.readTree("""
                {"type":"ANONYMOUS_CHAT_MESSAGE_RECEIVED",
                 "data":{"roomId":1,"messageId":501,"sender":"OTHER","content":"안녕하세요",
                         "sentAt":"2026-10-28T01:00:00Z"}}
                """).toString());
    }

    @Test
    void duplicatedMessage_publish_sendsOnlySentToSender() {
        listener.publish(savedEvent(true));

        List<AnonymousMessageRedisEvent> events = capturePublishedEvents(1);
        assertThat(events.getFirst().receiverUserId()).isEqualTo(SENDER_ID);
        assertThat(events.getFirst().event().get("type").asString()).isEqualTo("ANONYMOUS_CHAT_MESSAGE_SENT");
    }

    @Test
    void senderPublishFails_publish_stillSendsReceivedToPartner() {
        willThrow(new IllegalStateException("redis down"))
                .given(publisher).publish(argThat(event -> event.receiverUserId().equals(SENDER_ID)));

        listener.publish(savedEvent(false));

        verify(publisher).publish(argThat(event -> event.receiverUserId().equals(PARTNER_ID)
                && typeOf(event.event()).equals("ANONYMOUS_CHAT_MESSAGE_RECEIVED")));
    }

    private List<AnonymousMessageRedisEvent> capturePublishedEvents(int count) {
        ArgumentCaptor<AnonymousMessageRedisEvent> captor = ArgumentCaptor.forClass(AnonymousMessageRedisEvent.class);
        verify(publisher, times(count)).publish(captor.capture());
        return captor.getAllValues();
    }

    private static String typeOf(JsonNode event) {
        return event.get("type").asString();
    }

    private static AnonymousMessageSavedEvent savedEvent(boolean duplicated) {
        return new AnonymousMessageSavedEvent(
                ROOM_ID,
                SENDER_ID,
                PARTNER_ID,
                MESSAGE_ID,
                CLIENT_MESSAGE_ID,
                "안녕하세요",
                LocalDateTime.of(2026, 10, 28, 1, 0),
                duplicated
        );
    }
}
