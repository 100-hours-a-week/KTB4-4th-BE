package kr.ktb.zura.needu.anonymouschat.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import kr.ktb.zura.needu.anonymouschat.ws.AnonymousChatEventSender;
import kr.ktb.zura.needu.anonymouschat.ws.AnonymousMessageRedisEvent;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class AnonymousMessageEventSubscriberTest {

    private static final byte[] CHANNEL =
            "backend:test:anonymouschat:message-events".getBytes(StandardCharsets.UTF_8);

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final AnonymousChatEventSender eventSender = mock(AnonymousChatEventSender.class);
    private final AnonymousMessageEventSubscriber subscriber =
            new AnonymousMessageEventSubscriber(jsonMapper, eventSender);

    @Test
    void publishedEventMessage_onMessage_sendsEventToReceiver() {
        AnonymousMessageRedisEvent event = new AnonymousMessageRedisEvent(
                2L,
                jsonMapper.readTree("""
                        {"type":"ANONYMOUS_CHAT_MESSAGE_RECEIVED",
                         "data":{"roomId":1,"messageId":101,"sender":"OTHER","content":"안녕하세요"}}
                        """)
        );

        subscriber.onMessage(toMessage(jsonMapper.writeValueAsString(event)), CHANNEL);

        verify(eventSender).sendToUser(eq(2L), argThat(sent ->
                sent instanceof JsonNode node
                        && node.get("type").asString().equals("ANONYMOUS_CHAT_MESSAGE_RECEIVED")
                        && node.get("data").get("messageId").asLong() == 101L));
    }

    @Test
    void malformedJson_onMessage_doesNotSend() {
        subscriber.onMessage(toMessage("{not-json"), CHANNEL);

        verify(eventSender, never()).sendToUser(anyLong(), any());
    }

    @Test
    void receiverUserIdMissing_onMessage_doesNotSend() {
        subscriber.onMessage(toMessage("{\"event\":{\"type\":\"ANONYMOUS_CHAT_MESSAGE_RECEIVED\"}}"), CHANNEL);

        verify(eventSender, never()).sendToUser(anyLong(), any());
    }

    @Test
    void eventMissing_onMessage_doesNotSend() {
        subscriber.onMessage(toMessage("{\"receiverUserId\":2}"), CHANNEL);

        verify(eventSender, never()).sendToUser(anyLong(), any());
    }

    @Test
    void eventTypeMissing_onMessage_doesNotSend() {
        subscriber.onMessage(toMessage("{\"receiverUserId\":2,\"event\":{\"data\":{\"roomId\":1}}}"), CHANNEL);

        verify(eventSender, never()).sendToUser(anyLong(), any());
    }

    private Message toMessage(String body) {
        return new DefaultMessage(CHANNEL, body.getBytes(StandardCharsets.UTF_8));
    }
}
