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
        RateLimitProperties.Policy policy = properties.policies().get(policyName);
        if (policy == null) {
            throw new IllegalArgumentException("Unknown rate limit policy: " + policyName);
        }

        String key = keyGenerator.generate("ratelimit", "usage", policyName, userId);
        return repository.increment(key, policy.window());
    }
}
