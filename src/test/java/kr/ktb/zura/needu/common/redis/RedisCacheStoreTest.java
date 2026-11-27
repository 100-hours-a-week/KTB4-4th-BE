package kr.ktb.zura.needu.common.redis;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.sentry.Sentry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RedisCacheStoreTest {

    private static final String CACHE_NAME = "test-cache";
    private static final String OTHER_CACHE_NAME = "other-cache";
    private static final String KEY = "backend:test:test:cache:1";
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final Duration FAILURE_REPORT_INTERVAL = Duration.ofMinutes(1);
    private static final Instant NOW = Instant.parse("2026-11-27T00:00:00Z");

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final Clock clock = mock(Clock.class);
    private final RedisCacheStore store = new RedisCacheStore(
            redisTemplate, jsonMapper, meterRegistry, clock, FAILURE_REPORT_INTERVAL);

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(clock.instant()).willReturn(NOW);
    }

    @Test
    void cachedValue_findOrLoad_returnsCachedValueWithoutLoading() {
        given(valueOperations.get(KEY)).willReturn("{\"amount\":1000,\"name\":\"cached\"}");
        CountingLoader loader = new CountingLoader(new CachedValue(2000L, "loaded"));

        CachedValue value = store.findOrLoad(CACHE_NAME, KEY, CachedValue.class, TTL, loader);

        assertThat(value).isEqualTo(new CachedValue(1000L, "cached"));
        assertThat(loader.callCount).isZero();
        assertThat(cacheGets("hit")).isEqualTo(1.0);
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void cacheMiss_findOrLoad_savesLoadedValueWithTtl() {
        CountingLoader loader = new CountingLoader(new CachedValue(2000L, "loaded"));

        CachedValue value = store.findOrLoad(CACHE_NAME, KEY, CachedValue.class, TTL, loader);

        assertThat(value).isEqualTo(new CachedValue(2000L, "loaded"));
        assertThat(loader.callCount).isEqualTo(1);
        assertThat(cacheGets("miss")).isEqualTo(1.0);
        verify(valueOperations).set(KEY, jsonMapper.writeValueAsString(value), TTL);
    }

    @Test
    void loaderReturnsNull_findOrLoad_doesNotSave() {
        CachedValue value = store.findOrLoad(CACHE_NAME, KEY, CachedValue.class, TTL, () -> null);

        assertThat(value).isNull();
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void valueWithNullFields_findOrLoad_savesAndRestoresValue() {
        CachedValue emptyValue = new CachedValue(null, null);
        store.findOrLoad(CACHE_NAME, KEY, CachedValue.class, TTL, () -> emptyValue);
        String savedJson = jsonMapper.writeValueAsString(emptyValue);
        verify(valueOperations).set(KEY, savedJson, TTL);
        given(valueOperations.get(KEY)).willReturn(savedJson);

        CachedValue value = store.findOrLoad(CACHE_NAME, KEY, CachedValue.class, TTL, () -> new CachedValue(1L, "x"));

        assertThat(value).isEqualTo(emptyValue);
    }

    @Test
    void malformedCachedValue_findOrLoad_treatsAsMissAndOverwrites() {
        given(valueOperations.get(KEY)).willReturn("{invalid");
        CachedValue loadedValue = new CachedValue(2000L, "loaded");

        CachedValue value = store.findOrLoad(CACHE_NAME, KEY, CachedValue.class, TTL, () -> loadedValue);

        assertThat(value).isEqualTo(loadedValue);
        assertThat(cacheGets("miss")).isEqualTo(1.0);
        verify(valueOperations).set(KEY, jsonMapper.writeValueAsString(loadedValue), TTL);
    }

    @Test
    void loaderThrows_findOrLoad_propagatesException() {
        Supplier<CachedValue> loader = () -> {
            throw new IllegalStateException("db failure");
        };

        assertThatThrownBy(() -> store.findOrLoad(CACHE_NAME, KEY, CachedValue.class, TTL, loader))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("db failure");
    }

    @Test
    void redisGetFails_findOrLoad_returnsLoadedValueWithoutSaving() {
        given(valueOperations.get(KEY)).willThrow(new RedisConnectionFailureException("down"));
        CachedValue loadedValue = new CachedValue(2000L, "loaded");

        try (MockedStatic<Sentry> sentry = mockStatic(Sentry.class, CALLS_REAL_METHODS)) {
            CachedValue value = store.findOrLoad(CACHE_NAME, KEY, CachedValue.class, TTL, () -> loadedValue);

            assertThat(value).isEqualTo(loadedValue);
            verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
            sentry.verify(() -> Sentry.captureException(any(RedisConnectionFailureException.class)), times(1));
        }
    }

    @Test
    void redisSetFails_findOrLoad_returnsLoadedValue() {
        willThrow(new RedisConnectionFailureException("down"))
                .given(valueOperations).set(anyString(), anyString(), any(Duration.class));
        CachedValue loadedValue = new CachedValue(2000L, "loaded");

        try (MockedStatic<Sentry> sentry = mockStatic(Sentry.class, CALLS_REAL_METHODS)) {
            CachedValue value = store.findOrLoad(CACHE_NAME, KEY, CachedValue.class, TTL, () -> loadedValue);

            assertThat(value).isEqualTo(loadedValue);
            sentry.verify(() -> Sentry.captureException(any(RedisConnectionFailureException.class)), times(1));
        }
    }

    @Test
    void keys_delete_deletesAllKeys() {
        store.delete(CACHE_NAME, KEY, "other-key");

        verify(redisTemplate).delete(List.of(KEY, "other-key"));
    }

    @Test
    void redisDeleteFails_delete_doesNotThrow() {
        given(redisTemplate.delete(List.of(KEY))).willThrow(new RedisConnectionFailureException("down"));

        try (MockedStatic<Sentry> sentry = mockStatic(Sentry.class, CALLS_REAL_METHODS)) {
            store.delete(CACHE_NAME, KEY);

            sentry.verify(() -> Sentry.captureException(any(RedisConnectionFailureException.class)), times(1));
        }
    }

    @Test
    void repeatedFailuresWithinInterval_findOrLoad_reportsOnce() {
        given(valueOperations.get(KEY)).willThrow(new RedisConnectionFailureException("down"));

        try (MockedStatic<Sentry> sentry = mockStatic(Sentry.class, CALLS_REAL_METHODS)) {
            store.findOrLoad(CACHE_NAME, KEY, CachedValue.class, TTL, () -> null);
            given(clock.instant()).willReturn(NOW.plus(FAILURE_REPORT_INTERVAL).minusSeconds(1));
            store.findOrLoad(CACHE_NAME, KEY, CachedValue.class, TTL, () -> null);

            sentry.verify(() -> Sentry.captureException(any(RedisConnectionFailureException.class)), times(1));
        }
    }

    @Test
    void failureAfterInterval_findOrLoad_reportsAgain() {
        given(valueOperations.get(KEY)).willThrow(new RedisConnectionFailureException("down"));

        try (MockedStatic<Sentry> sentry = mockStatic(Sentry.class, CALLS_REAL_METHODS)) {
            store.findOrLoad(CACHE_NAME, KEY, CachedValue.class, TTL, () -> null);
            given(clock.instant()).willReturn(NOW.plus(FAILURE_REPORT_INTERVAL));
            store.findOrLoad(CACHE_NAME, KEY, CachedValue.class, TTL, () -> null);

            sentry.verify(() -> Sentry.captureException(any(RedisConnectionFailureException.class)), times(2));
        }
    }

    @Test
    void failuresInDifferentCaches_findOrLoad_reportsEachCache() {
        given(valueOperations.get(KEY)).willThrow(new RedisConnectionFailureException("down"));

        try (MockedStatic<Sentry> sentry = mockStatic(Sentry.class, CALLS_REAL_METHODS)) {
            store.findOrLoad(CACHE_NAME, KEY, CachedValue.class, TTL, () -> null);
            store.findOrLoad(OTHER_CACHE_NAME, KEY, CachedValue.class, TTL, () -> null);

            sentry.verify(() -> Sentry.captureException(any(RedisConnectionFailureException.class)), times(2));
        }
    }

    private double cacheGets(String result) {
        return meterRegistry.get("cache.gets")
                .tags("cache", CACHE_NAME, "result", result)
                .counter()
                .count();
    }

    record CachedValue(Long amount, String name) {
    }

    private static class CountingLoader implements Supplier<CachedValue> {

        private final CachedValue value;
        private int callCount;

        private CountingLoader(CachedValue value) {
            this.value = value;
        }

        @Override
        public CachedValue get() {
            callCount++;
            return value;
        }
    }
}
