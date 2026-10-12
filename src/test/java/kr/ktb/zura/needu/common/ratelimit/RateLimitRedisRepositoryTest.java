package kr.ktb.zura.needu.common.ratelimit;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@SuppressWarnings("rawtypes")
class RateLimitRedisRepositoryTest {

    private static final String KEY = "backend:test:ratelimit:usage:friend-list:1";

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final RateLimitRedisRepository repository = new RateLimitRedisRepository(redisTemplate);

    @Test
    void increment_executesAtomicScriptAndReturnsUsage() {
        given(redisTemplate.execute(
                ArgumentMatchers.<RedisScript<List>>any(),
                org.mockito.ArgumentMatchers.eq(List.of(KEY)),
                org.mockito.ArgumentMatchers.eq("60000")
        )).willReturn(List.of(2L, 40_000L));

        RateLimitUsage usage = repository.increment(KEY, Duration.ofMinutes(1));

        assertThat(usage).isEqualTo(new RateLimitUsage(2, Duration.ofSeconds(40)));
        verify(redisTemplate).execute(
                ArgumentMatchers.<RedisScript<List>>any(),
                org.mockito.ArgumentMatchers.eq(List.of(KEY)),
                org.mockito.ArgumentMatchers.eq("60000")
        );
    }

    @Test
    void invalidScriptResult_increment_throwsIllegalStateException() {
        assertThatThrownBy(() -> repository.increment(KEY, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalStateException.class);
    }
}
