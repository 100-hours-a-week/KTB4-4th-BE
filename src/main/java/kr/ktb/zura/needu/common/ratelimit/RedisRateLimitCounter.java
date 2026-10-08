package kr.ktb.zura.needu.common.ratelimit;

import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;

public class RedisRateLimitCounter implements RateLimitCounter {

    private final RateLimitProperties properties;
    private final RateLimitRedisRepository repository;
    private final RedisKeyGenerator keyGenerator;

    public RedisRateLimitCounter(
            RateLimitProperties properties,
            RateLimitRedisRepository repository,
            RedisKeyGenerator keyGenerator
    ) {
        this.properties = properties;
        this.repository = repository;
        this.keyGenerator = keyGenerator;
    }

    @Override
    public RateLimitUsage increment(String policyName, String userId) {
        // TODO: 정책 조회 후 Redis 키를 생성해 Repository 호출
        throw new UnsupportedOperationException("Redis rate limit counter is not implemented");
    }
}
