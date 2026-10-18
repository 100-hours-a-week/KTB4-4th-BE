package kr.ktb.zura.needu.common.ratelimit;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import kr.ktb.zura.needu.common.exception.ErrorResponseWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class RateLimitFilterTest {

    private static final String POLICY_NAME = "personal-recommendations";
    private static final String PATH = "/api/v1/users/me/personal-recommendations";
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final long LIMIT = 2;
    private static final Long USER_ID = 1L;

    private RateLimitCounter rateLimitCounter;
    private RateLimitFilter rateLimitFilter;

    @BeforeEach
    void setUp() {
        RateLimitProperties properties = new RateLimitProperties(Map.of(
                POLICY_NAME, new RateLimitProperties.Policy(HttpMethod.GET, PATH, LIMIT, WINDOW)
        ));
        rateLimitCounter = mock(RateLimitCounter.class);
        rateLimitFilter = new RateLimitFilter(
                properties,
                rateLimitCounter,
                new ErrorResponseWriter(JsonMapper.builder().build()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requestsWithinLimit_doFilter_passesToNextFilter() throws Exception {
        authenticate(USER_ID);
        given(rateLimitCounter.increment(POLICY_NAME, USER_ID.toString()))
                .willReturn(new RateLimitUsage(LIMIT, WINDOW));

        for (int i = 0; i < LIMIT; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain filterChain = new MockFilterChain();

            rateLimitFilter.doFilter(new MockHttpServletRequest("GET", PATH), response, filterChain);

            assertThat(filterChain.getRequest()).isNotNull();
            assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        }
    }

    @Test
    void requestOverLimit_doFilter_returnsTooManyRequestsWithRemainingSeconds() throws Exception {
        authenticate(USER_ID);
        given(rateLimitCounter.increment(POLICY_NAME, USER_ID.toString()))
                .willReturn(new RateLimitUsage(LIMIT + 1, Duration.ofSeconds(10)));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        rateLimitFilter.doFilter(new MockHttpServletRequest("GET", PATH), response, filterChain);

        assertThat(filterChain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(response.getHeader("Retry-After")).isEqualTo("10");
        assertThat(response.getContentAsString())
                .contains("\"message\":\"요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.\"")
                .contains("\"retryAfterSeconds\":10");
    }

    @Test
    void redisUnavailable_doFilter_returnsServiceUnavailable() throws Exception {
        authenticate(USER_ID);
        given(rateLimitCounter.increment(POLICY_NAME, USER_ID.toString()))
                .willThrow(new DataAccessResourceFailureException("Redis unavailable"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        rateLimitFilter.doFilter(new MockHttpServletRequest("GET", PATH), response, filterChain);

        assertThat(filterChain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(response.getContentAsString())
                .contains("\"message\":\"일시적으로 서비스를 이용할 수 없습니다.\"");
    }

    @Test
    void notLimitedPath_doFilter_skipsRateLimit() throws Exception {
        authenticate(USER_ID);

        assertThat(sendRequests("/api/v1/guidance", "GET", LIMIT + 1)).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void notLimitedMethod_doFilter_skipsRateLimit() throws Exception {
        authenticate(USER_ID);

        assertThat(sendRequests(PATH, "POST", LIMIT + 1)).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void unauthenticatedRequest_doFilter_skipsRateLimit() throws Exception {
        assertThat(sendRequests(PATH, "GET", LIMIT + 1)).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void overlappingPathPatterns_doFilter_appliesMostSpecificPolicy() throws Exception {
        RateLimitProperties properties = new RateLimitProperties(Map.of(
                "gift-recommendations", new RateLimitProperties.Policy(
                        HttpMethod.GET, "/api/v1/users/{userId}/gift-recommendations", LIMIT + 10, WINDOW),
                "my-gift-recommendations", new RateLimitProperties.Policy(
                        HttpMethod.GET, "/api/v1/users/me/gift-recommendations", LIMIT, WINDOW)
        ));
        rateLimitFilter = new RateLimitFilter(
                properties,
                rateLimitCounter,
                new ErrorResponseWriter(JsonMapper.builder().build()));
        authenticate(USER_ID);
        given(rateLimitCounter.increment(anyString(), anyString()))
                .willReturn(new RateLimitUsage(1, WINDOW));
        given(rateLimitCounter.increment("my-gift-recommendations", USER_ID.toString()))
                .willReturn(new RateLimitUsage(LIMIT + 1, WINDOW));

        assertThat(sendRequests("/api/v1/users/321/gift-recommendations", "GET", LIMIT + 1))
                .isEqualTo(HttpStatus.OK.value());
        assertThat(sendRequests("/api/v1/users/me/gift-recommendations", "GET", LIMIT + 1))
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    private int sendRequests(String path, String method, long count) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        for (int i = 0; i < count; i++) {
            response = new MockHttpServletResponse();
            rateLimitFilter.doFilter(new MockHttpServletRequest(method, path), response, new MockFilterChain());
        }
        return response.getStatus();
    }

    private void authenticate(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(userId, null, List.of()));
    }
}
