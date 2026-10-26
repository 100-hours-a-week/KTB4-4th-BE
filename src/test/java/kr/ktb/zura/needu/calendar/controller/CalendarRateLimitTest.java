package kr.ktb.zura.needu.calendar.controller;

import java.time.Duration;
import java.util.List;
import kr.ktb.zura.needu.common.exception.ErrorResponseWriter;
import kr.ktb.zura.needu.common.ratelimit.RateLimitCounter;
import kr.ktb.zura.needu.common.ratelimit.RateLimitFilter;
import kr.ktb.zura.needu.common.ratelimit.RateLimitProperties;
import kr.ktb.zura.needu.common.ratelimit.RateLimitUsage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class CalendarRateLimitTest {

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @CsvSource({
            "calendar-monthly-summary,/api/v1/calendar/events",
            "calendar-daily-list,/api/v1/calendar/events/daily"
    })
    void configuredPolicy_allows120AndRejects121(String policyName, String path) throws Exception {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("rate-limit.yml"));
        var properties = new Binder(new MapConfigurationPropertySource(yaml.getObject()))
                .bind("needu.rate-limit", Bindable.of(RateLimitProperties.class)).get();
        var policy = properties.policies().get(policyName);
        assertThat(policy.method()).isEqualTo(HttpMethod.GET);
        assertThat(policy.limit()).isEqualTo(120);
        assertThat(policy.window()).isEqualTo(Duration.ofMinutes(1));

        RateLimitCounter counter = mock(RateLimitCounter.class);
        given(counter.increment(policyName, "1")).willReturn(
                new RateLimitUsage(120, Duration.ofSeconds(10)),
                new RateLimitUsage(121, Duration.ofSeconds(10)));
        var filter = new RateLimitFilter(properties, counter, new ErrorResponseWriter(JsonMapper.builder().build()));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(1L, null, List.of()));

        var allowedChain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("GET", path), new MockHttpServletResponse(), allowedChain);
        assertThat(allowedChain.getRequest()).isNotNull();

        var response = new MockHttpServletResponse();
        var blockedChain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("GET", path), response, blockedChain);
        assertThat(blockedChain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("10");
        assertThat(response.getContentAsString()).contains("\"retryAfterSeconds\":10");
    }
}
