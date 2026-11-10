package kr.ktb.zura.needu.common.ratelimit;

import java.time.Duration;

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
        Duration window = findWindow(policyName);
        String key = keyGenerator.generate("ratelimit", "usage", policyName, userId);
        return repository.increment(key, window);
    }

    private Duration findWindow(String policyName) {
        RateLimitProperties.Policy policy = properties.policies().get(policyName);
        if (policy != null) {
            return policy.window();
        }
        RateLimitProperties.EventPolicy eventPolicy = properties.events().get(policyName);
        if (eventPolicy != null) {
            return eventPolicy.window();
        }
        throw new IllegalArgumentException("Unknown rate limit policy: " + policyName);
    }
}
