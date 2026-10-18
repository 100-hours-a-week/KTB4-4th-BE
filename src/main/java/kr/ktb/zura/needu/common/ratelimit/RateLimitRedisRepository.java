package kr.ktb.zura.needu.common.ratelimit;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
public class RateLimitRedisRepository {

    private static final DefaultRedisScript<List> INCREMENT_SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            local ttl = redis.call('PTTL', KEYS[1])
            if count == 1 or ttl < 0 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
                ttl = tonumber(ARGV[1])
            end
            return { count, ttl }
            """, List.class);

    private final StringRedisTemplate redisTemplate;

    public RateLimitRedisRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public RateLimitUsage increment(String key, Duration window) {
        List<?> result = redisTemplate.execute(
                INCREMENT_SCRIPT,
                List.of(key),
                String.valueOf(window.toMillis())
        );
        if (result == null || result.size() != 2) {
            throw new IllegalStateException("Invalid Redis rate limit result");
        }

        long count = Long.parseLong(String.valueOf(result.get(0)));
        long remainingMillis = Long.parseLong(String.valueOf(result.get(1)));
        return new RateLimitUsage(count, Duration.ofMillis(remainingMillis));
    }
}
