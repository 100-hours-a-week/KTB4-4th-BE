package kr.ktb.zura.needu.common.redis;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.sentry.Sentry;
import io.sentry.SentryLevel;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
public class RedisCacheStore {

    private static final String CACHE_GETS_METRIC = "cache.gets";
    private static final String CACHE_UNAVAILABLE_ERROR_CODE = "REDIS_CACHE_UNAVAILABLE";

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final Duration failureReportInterval;
    private final Map<String, Instant> lastFailureReportedAt = new ConcurrentHashMap<>();

    public RedisCacheStore(
            StringRedisTemplate redisTemplate,
            JsonMapper jsonMapper,
            MeterRegistry meterRegistry,
            Clock clock,
            @Value("${needu.redis-cache.failure-report-interval}") Duration failureReportInterval
    ) {
        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
        this.failureReportInterval = failureReportInterval;
    }

    /**
     * 캐시 값을 반환하고, 없으면 loader 결과를 ttl 동안 저장한 뒤 반환한다. loader가 null을 반환하면 저장하지 않는다.
     */
    public <T> T findOrLoad(String cacheName, String key, Class<T> type, Duration ttl, Supplier<T> loader) {
        String cachedValue;
        try {
            cachedValue = redisTemplate.opsForValue().get(key);
        } catch (DataAccessException e) {
            reportFailure(cacheName, "find", e);
            return loader.get();
        }

        if (cachedValue != null) {
            T value = deserialize(cacheName, cachedValue, type);
            if (value != null) {
                countGet(cacheName, "hit");
                return value;
            }
        }

        countGet(cacheName, "miss");
        T loadedValue = loader.get();
        if (loadedValue != null) {
            save(cacheName, key, loadedValue, ttl);
        }
        return loadedValue;
    }

    public void delete(String cacheName, String... keys) {
        try {
            redisTemplate.delete(List.of(keys));
        } catch (DataAccessException e) {
            reportFailure(cacheName, "delete", e);
        }
    }

    private void save(String cacheName, String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, jsonMapper.writeValueAsString(value), ttl);
        } catch (DataAccessException e) {
            reportFailure(cacheName, "save", e);
        }
    }

    private <T> T deserialize(String cacheName, String cachedValue, Class<T> type) {
        try {
            return jsonMapper.readValue(cachedValue, type);
        } catch (JacksonException e) {
            log.warn("Redis cache value deserialization failed. cache={}", cacheName);
            return null;
        }
    }

    private void countGet(String cacheName, String result) {
        Counter.builder(CACHE_GETS_METRIC)
                .tag("cache", cacheName)
                .tag("result", result)
                .register(meterRegistry)
                .increment();
    }

    private void reportFailure(String cacheName, String operation, DataAccessException e) {
        if (!shouldReportFailure(cacheName)) {
            return;
        }
        log.warn("Redis cache access failed. cache={}, operation={}", cacheName, operation, e);
        Sentry.withScope(scope -> {
            scope.setLevel(SentryLevel.WARNING);
            scope.setTag("cache.name", cacheName);
            scope.setTag("cache.operation", operation);
            scope.setTag("error.code", CACHE_UNAVAILABLE_ERROR_CODE);
            Sentry.captureException(e);
        });
    }

    // 장애 중에는 요청마다 실패하므로 캐시 이름별로 간격마다 한 번만 보고한다
    private boolean shouldReportFailure(String cacheName) {
        Instant now = clock.instant();
        Instant lastReportedAt = lastFailureReportedAt.get(cacheName);
        if (lastReportedAt == null) {
            return lastFailureReportedAt.putIfAbsent(cacheName, now) == null;
        }
        if (now.isBefore(lastReportedAt.plus(failureReportInterval))) {
            return false;
        }
        return lastFailureReportedAt.replace(cacheName, lastReportedAt, now);
    }
}
