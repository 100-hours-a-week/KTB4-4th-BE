package kr.ktb.zura.needu.notification.service;

import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;
import kr.ktb.zura.needu.notification.sse.NotificationRedisEvent;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
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
        // TODO: 이벤트를 JSON으로 직렬화해 notification events 채널에 발행
        throw new UnsupportedOperationException("Notification event publishing is not implemented");
    }
}
