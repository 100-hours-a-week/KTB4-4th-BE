package kr.ktb.zura.needu.user.repository;

import java.time.Duration;
import java.util.List;
import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
public class UserWithdrawalLockRepository {

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyGenerator keyGenerator;
    private final Duration ttl;

    public UserWithdrawalLockRepository(
            StringRedisTemplate redisTemplate,
            RedisKeyGenerator keyGenerator,
            @Value("${needu.user.withdrawal.lock-ttl}") Duration ttl
    ) {
        this.redisTemplate = redisTemplate;
        this.keyGenerator = keyGenerator;
        this.ttl = ttl;
    }

    public boolean create(Long userId, String ownerToken) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue()
                .setIfAbsent(generateKey(userId), ownerToken, ttl));
    }

    public void delete(Long userId, String ownerToken) {
        redisTemplate.execute(RELEASE_SCRIPT, List.of(generateKey(userId)), ownerToken);
    }

    private String generateKey(Long userId) {
        return keyGenerator.generate("user", "withdrawal", userId);
    }
}
