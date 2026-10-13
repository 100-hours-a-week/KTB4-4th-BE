package kr.ktb.zura.needu.aichat.repository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
public class AiGenerationStateRepository {

    private static final DefaultRedisScript<Long> DELETE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyGenerator keyGenerator;
    private final Duration ttl;

    public AiGenerationStateRepository(
            StringRedisTemplate redisTemplate,
            RedisKeyGenerator keyGenerator,
            @Value("${ai.chat.generation-ttl}") Duration ttl
    ) {
        this.redisTemplate = redisTemplate;
        this.keyGenerator = keyGenerator;
        this.ttl = ttl;
    }

    public void save(Long conversationId, Long userMessageId) {
        redisTemplate.opsForValue().set(generateKey(conversationId), String.valueOf(userMessageId), ttl);
    }

    public Optional<Long> findUserMessageId(Long conversationId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(generateKey(conversationId)))
                .map(Long::valueOf);
    }

    public void delete(Long conversationId, Long userMessageId) {
        redisTemplate.execute(DELETE_SCRIPT, List.of(generateKey(conversationId)), String.valueOf(userMessageId));
    }

    private String generateKey(Long conversationId) {
        return keyGenerator.generate("aichat", "generating", conversationId);
    }
}
