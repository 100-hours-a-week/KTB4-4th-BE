package kr.ktb.zura.needu.aichat.service;

import java.nio.charset.StandardCharsets;

import kr.ktb.zura.needu.aichat.sse.AiChatEventType;
import kr.ktb.zura.needu.aichat.sse.AiChatRedisEvent;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;
import tools.jackson.databind.json.JsonMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AiChatEventSubscriberTest {

    private static final byte[] CHANNEL = "backend:test:aichat:events".getBytes(StandardCharsets.UTF_8);

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final AiChatStreamService aiChatStreamService = mock(AiChatStreamService.class);
    private final AiChatEventSubscriber subscriber = new AiChatEventSubscriber(jsonMapper, aiChatStreamService);

    @Test
    void publishedEventMessage_onMessage_sendsToStreamService() {
        AiChatRedisEvent event = new AiChatRedisEvent(101L, AiChatEventType.DELTA,
                jsonMapper.createObjectNode().put("userMessageId", 201L).put("index", 0).put("text", "좋"));

        subscriber.onMessage(toMessage(jsonMapper.writeValueAsString(event)), CHANNEL);

        verify(aiChatStreamService).sendEvent(argThat(received ->
                received.conversationId().equals(101L)
                        && received.type() == AiChatEventType.DELTA
                        && received.data().get("text").asString().equals("좋")));
    }

    @Test
    void malformedJson_onMessage_doesNotSend() {
        subscriber.onMessage(toMessage("{not-json"), CHANNEL);

        verify(aiChatStreamService, never()).sendEvent(any());
    }

    @Test
    void conversationIdMissing_onMessage_doesNotSend() {
        subscriber.onMessage(toMessage("{\"type\":\"DONE\",\"data\":{\"userMessageId\":201}}"), CHANNEL);

        verify(aiChatStreamService, never()).sendEvent(any());
    }

    @Test
    void unknownType_onMessage_doesNotSend() {
        subscriber.onMessage(toMessage(
                "{\"conversationId\":101,\"type\":\"UNKNOWN\",\"data\":{\"userMessageId\":201}}"), CHANNEL);

        verify(aiChatStreamService, never()).sendEvent(any());
    }

    private Message toMessage(String body) {
        return new DefaultMessage(CHANNEL, body.getBytes(StandardCharsets.UTF_8));
    }
}
