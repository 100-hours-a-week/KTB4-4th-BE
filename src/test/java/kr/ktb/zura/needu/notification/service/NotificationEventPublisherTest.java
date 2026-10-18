package kr.ktb.zura.needu.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;
import kr.ktb.zura.needu.notification.sse.NotificationRedisEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

class NotificationEventPublisherTest {

    @Test
    void notificationEvent_publish_sendsJsonToNotificationChannel() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        JsonMapper jsonMapper = JsonMapper.builder().build();
        NotificationEventPublisher publisher = new NotificationEventPublisher(
                redisTemplate,
                jsonMapper,
                new RedisKeyGenerator("needu:test")
        );
        NotificationRedisEvent event = new NotificationRedisEvent(
                1L,
                jsonMapper.readTree("{\"notificationId\":10}"),
                3L
        );

        publisher.publish(event);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq("needu:test:notification:events"),
                payloadCaptor.capture()
        );
        assertThat(jsonMapper.readTree(payloadCaptor.getValue()).toString())
                .isEqualTo(jsonMapper.valueToTree(event).toString());
    }
}
