package kr.ktb.zura.needu.common.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class CaffeineRateLimitCounter implements RateLimitCounter {

    private final Map<String, PolicyCounter> counters;

    public CaffeineRateLimitCounter(RateLimitProperties properties) {
        this(properties, Ticker.systemTicker());
    }

    CaffeineRateLimitCounter(RateLimitProperties properties, Ticker ticker) {
        this.counters = properties.policies().entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> PolicyCounter.from(entry.getValue(), ticker)
                ));
    }

    @Override
    public RateLimitUsage increment(String policyName, String userId) {
        PolicyCounter counter = counters.get(policyName);
        if (counter == null) {
            throw new IllegalArgumentException("Unknown rate limit policy: " + policyName);
        }
        return counter.increment(userId);
    }

    private record PolicyCounter(Duration window, Cache<String, AtomicLong> counts) {

        static PolicyCounter from(RateLimitProperties.Policy policy, Ticker ticker) {
            // 카운터 값만 증가시키고 캐시에 다시 쓰지 않으므로, 첫 요청 시점부터 window가 지나면 만료되는 고정 윈도우가 된다.
            // maximumSize로 사용자 수가 몰려도 메모리 사용량이 일정 크기를 넘지 않게 한다.
            Cache<String, AtomicLong> counts = Caffeine.newBuilder()
                    .expireAfterWrite(policy.window())
                    .maximumSize(policy.maximumSize())
                    .ticker(ticker)
                    .build();
            return new PolicyCounter(policy.window(), counts);
        }

        RateLimitUsage increment(String userId) {
            long count = counts.get(userId, key -> new AtomicLong()).incrementAndGet();
            return new RateLimitUsage(count, findRemaining(userId));
        }

        private Duration findRemaining(String userId) {
            Duration age = counts.policy().expireAfterWrite()
                    .flatMap(expiration -> expiration.ageOf(userId))
                    .orElse(Duration.ZERO);
            return window.minus(age);
        }
    }
}
