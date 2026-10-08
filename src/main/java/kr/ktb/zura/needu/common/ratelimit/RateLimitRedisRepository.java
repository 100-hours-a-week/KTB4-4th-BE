package kr.ktb.zura.needu.common.ratelimit;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RateLimitRedisRepository {

    private final StringRedisTemplate redisTemplate;

    public RateLimitRedisRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public RateLimitUsage increment(String key, Duration window) {
        // TODO: Lua로 INCR, 최초 TTL 설정, 남은 TTL 조회를 원자 처리
        throw new UnsupportedOperationException("Redis rate limit repository is not implemented");
    }
}
