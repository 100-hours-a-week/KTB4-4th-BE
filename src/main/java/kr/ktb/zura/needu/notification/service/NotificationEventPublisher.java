package kr.ktb.zura.needu.notification.service;

import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;
import kr.ktb.zura.needu.notification.sse.NotificationRedisEvent;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

// DB 커밋 이후 알림 이벤트를 JSON으로 직렬화해 모든 BE 인스턴스에 발행한다.
@Component
public class NotificationEventPublisher {

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final RedisKeyGenerator keyGenerator;

    public NotificationEventPublisher(
            StringRedisTemplate redisTemplate,
            JsonMapper jsonMapper,
            RedisKeyGenerator keyGenerator
    ) {
        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
        this.keyGenerator = keyGenerator;
    }

    public void publish(NotificationRedisEvent event) {
        try {
            redisTemplate.convertAndSend(
                    keyGenerator.generate("notification", "events"),
                    jsonMapper.writeValueAsString(event)
            );
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialize notification Redis event.", exception);
        }
    }
}
