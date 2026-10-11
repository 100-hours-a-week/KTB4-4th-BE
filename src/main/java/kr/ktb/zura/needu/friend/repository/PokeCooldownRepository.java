package kr.ktb.zura.needu.friend.repository;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
public class PokeCooldownRepository {

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyGenerator keyGenerator;

    public PokeCooldownRepository(StringRedisTemplate redisTemplate, RedisKeyGenerator keyGenerator) {
        this.redisTemplate = redisTemplate;
        this.keyGenerator = keyGenerator;
    }

    public boolean create(Long senderUserId, Long receiverUserId, String requestId, Duration cooldown) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue()
                .setIfAbsent(generateKey(senderUserId, receiverUserId), requestId, cooldown));
    }

    public long findRemainingSeconds(Long senderUserId, Long receiverUserId) {
        Long remainingSeconds = redisTemplate.getExpire(
                generateKey(senderUserId, receiverUserId), TimeUnit.SECONDS);
        return remainingSeconds == null || remainingSeconds < 1 ? 1 : remainingSeconds;
    }

    public void delete(Long senderUserId, Long receiverUserId, String requestId) {
        redisTemplate.execute(
                RELEASE_SCRIPT,
                List.of(generateKey(senderUserId, receiverUserId)),
                requestId
        );
    }

    private String generateKey(Long senderUserId, Long receiverUserId) {
        return keyGenerator.generate("friend", "poke", senderUserId, receiverUserId);
    }
}
