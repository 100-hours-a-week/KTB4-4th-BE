package kr.ktb.zura.needu.common.ratelimit;

import java.time.Duration;
import java.util.Map;

import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class RedisRateLimitCounterTest {

    private static final String POLICY_NAME = "friend-list";
    private static final String USER_ID = "1";
    private static final String KEY = "backend:test:ratelimit:usage:friend-list:1";
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final RateLimitRedisRepository repository = mock(RateLimitRedisRepository.class);
    private final RedisKeyGenerator keyGenerator = mock(RedisKeyGenerator.class);
    private final RedisRateLimitCounter rateLimitCounter = new RedisRateLimitCounter(
            new RateLimitProperties(Map.of(
                    POLICY_NAME, new RateLimitProperties.Policy(
                            HttpMethod.GET, "/api/v1/friends", 10, WINDOW)
            )),
            repository,
            keyGenerator
    );

    @Test
    void knownPolicy_increment_usesSharedRedisKeyAndWindow() {
        RateLimitUsage expected = new RateLimitUsage(2, Duration.ofSeconds(40));
        given(keyGenerator.generate("ratelimit", "usage", POLICY_NAME, USER_ID)).willReturn(KEY);
        given(repository.increment(KEY, WINDOW)).willReturn(expected);

        RateLimitUsage usage = rateLimitCounter.increment(POLICY_NAME, USER_ID);

        assertThat(usage).isEqualTo(expected);
        verify(repository).increment(KEY, WINDOW);
    }

    @Test
    void unknownPolicy_increment_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> rateLimitCounter.increment("unknown", USER_ID))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository, keyGenerator);
    }
}
