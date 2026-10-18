package kr.ktb.zura.needu.aichat.service;

import kr.ktb.zura.needu.aichat.sse.AiChatDeltaEvent;
import kr.ktb.zura.needu.aichat.sse.AiChatEventType;
import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AiChatEventPublisherTest {

    @Test
    void deltaEvent_publish_sendsJsonToAiChatChannel() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        JsonMapper jsonMapper = JsonMapper.builder().build();
        AiChatEventPublisher publisher = new AiChatEventPublisher(
                redisTemplate, jsonMapper, new RedisKeyGenerator("needu:test"));

        publisher.publish(101L, AiChatEventType.DELTA, new AiChatDeltaEvent(201L, 0, "좋"));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(eq("needu:test:aichat:events"), payloadCaptor.capture());
        JsonNode payload = jsonMapper.readTree(payloadCaptor.getValue());
        assertThat(payload.get("conversationId").asLong()).isEqualTo(101L);
        assertThat(payload.get("type").asString()).isEqualTo("DELTA");
        assertThat(payload.get("data").get("userMessageId").asLong()).isEqualTo(201L);
        assertThat(payload.get("data").get("index").asInt()).isZero();
        assertThat(payload.get("data").get("text").asString()).isEqualTo("좋");
    }
}
