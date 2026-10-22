package kr.ktb.zura.needu.aichat.repository;

import java.util.List;
import kr.ktb.zura.needu.aichat.config.AiConversationLockProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

// Redis 락 획득과 소유자 확인 후 해제는 이 Repository에서만 구현한다.
@Repository
public class AiConversationLockRepository {

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final AiConversationLockProperties properties;

    public AiConversationLockRepository(
            StringRedisTemplate redisTemplate,
            AiConversationLockProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public boolean create(Long conversationId, String ownerToken) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue()
                .setIfAbsent(generateKey(conversationId), ownerToken, properties.ttl()));
    }

    public boolean exists(Long conversationId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(generateKey(conversationId)));
    }

    public void delete(Long conversationId, String ownerToken) {
        redisTemplate.execute(RELEASE_SCRIPT, List.of(generateKey(conversationId)), ownerToken);
    }

    private String generateKey(Long conversationId) {
        return properties.keyPrefix() + ":" + conversationId;
    }
}
