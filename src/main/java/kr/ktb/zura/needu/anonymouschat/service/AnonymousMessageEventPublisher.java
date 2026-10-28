package kr.ktb.zura.needu.anonymouschat.service;

import kr.ktb.zura.needu.anonymouschat.ws.AnonymousMessageRedisEvent;
import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class AnonymousMessageEventPublisher {

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final RedisKeyGenerator keyGenerator;

    public AnonymousMessageEventPublisher(
            StringRedisTemplate redisTemplate,
            JsonMapper jsonMapper,
            RedisKeyGenerator keyGenerator
    ) {
        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
        this.keyGenerator = keyGenerator;
    }

    public void publish(AnonymousMessageRedisEvent event) {
        try {
            redisTemplate.convertAndSend(
                    keyGenerator.generate("anonymouschat", "message-events"),
                    jsonMapper.writeValueAsString(event)
            );
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialize anonymous message Redis event.", exception);
        }
    }
}
