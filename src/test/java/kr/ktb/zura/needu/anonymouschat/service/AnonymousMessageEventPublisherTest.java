package kr.ktb.zura.needu.anonymouschat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import kr.ktb.zura.needu.anonymouschat.ws.AnonymousMessageRedisEvent;
import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

class AnonymousMessageEventPublisherTest {

    @Test
    void messageEvent_publish_sendsJsonToMessageEventsChannel() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        JsonMapper jsonMapper = JsonMapper.builder().build();
        AnonymousMessageEventPublisher publisher = new AnonymousMessageEventPublisher(
                redisTemplate,
                jsonMapper,
                new RedisKeyGenerator("backend:test")
        );
        AnonymousMessageRedisEvent event = new AnonymousMessageRedisEvent(
                2L,
                jsonMapper.readTree("{\"type\":\"ANONYMOUS_CHAT_MESSAGE_RECEIVED\",\"data\":{\"roomId\":1}}")
        );

        publisher.publish(event);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(
                eq("backend:test:anonymouschat:message-events"),
                payloadCaptor.capture()
        );
        assertThat(jsonMapper.readTree(payloadCaptor.getValue()).toString())
                .isEqualTo(jsonMapper.valueToTree(event).toString());
    }
}
