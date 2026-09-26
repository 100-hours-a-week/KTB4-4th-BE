package kr.ktb.zura.needu.common.config;

import kr.ktb.zura.needu.common.exception.ErrorResponseWriter;
import kr.ktb.zura.needu.common.ratelimit.CaffeineRateLimitCounter;
import kr.ktb.zura.needu.common.ratelimit.RateLimitCounter;
import kr.ktb.zura.needu.common.ratelimit.RateLimitFilter;
import kr.ktb.zura.needu.common.ratelimit.RateLimitProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

    // Redis로 전환할 때는 이 Bean만 Redis 구현체로 교체
    @Bean
    public RateLimitCounter rateLimitCounter(RateLimitProperties properties) {
        return new CaffeineRateLimitCounter(properties);
    }

    // 인증 사용자 기준으로 제한하려면 SecurityContext가 채워진 뒤여야 하므로 Spring Security 필터 체인보다 뒤에 등록한다.
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
            RateLimitProperties properties,
            RateLimitCounter rateLimitCounter,
            ErrorResponseWriter errorResponseWriter
    ) {
        FilterRegistrationBean<RateLimitFilter> registration =
                new FilterRegistrationBean<>(new RateLimitFilter(properties, rateLimitCounter, errorResponseWriter));
        registration.setOrder(Ordered.LOWEST_PRECEDENCE);
        return registration;
    }
}
