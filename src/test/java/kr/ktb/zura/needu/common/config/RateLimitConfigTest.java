package kr.ktb.zura.needu.common.config;

import kr.ktb.zura.needu.common.exception.ErrorResponseWriter;
import kr.ktb.zura.needu.common.ratelimit.RateLimitCounter;
import kr.ktb.zura.needu.common.ratelimit.RateLimitRedisRepository;
import kr.ktb.zura.needu.common.ratelimit.RedisRateLimitCounter;
import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RateLimitConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RateLimitConfig.class)
            .withBean(ErrorResponseWriter.class, () -> mock(ErrorResponseWriter.class))
            .withBean(RateLimitRedisRepository.class, () -> mock(RateLimitRedisRepository.class))
            .withBean(RedisKeyGenerator.class, () -> mock(RedisKeyGenerator.class))
            .withPropertyValues(
                    "needu.rate-limit.policies.test.method=GET",
                    "needu.rate-limit.policies.test.path-pattern=/test",
                    "needu.rate-limit.policies.test.limit=1",
                    "needu.rate-limit.policies.test.window=1m"
            );

    @Test
    void enabledByDefault_registersRateLimitFilter() {
        contextRunner.run(context -> {
            assertThat(context).hasBean("rateLimitFilterRegistration");
            assertThat(context.getBean(RateLimitCounter.class)).isInstanceOf(RedisRateLimitCounter.class);
        });
    }

    @Test
    void disabled_doesNotRegisterRateLimitFilter() {
        contextRunner.withPropertyValues("needu.rate-limit.enabled=false")
                .run(context -> assertThat(context)
                        .doesNotHaveBean("rateLimitFilterRegistration"));
    }
}
