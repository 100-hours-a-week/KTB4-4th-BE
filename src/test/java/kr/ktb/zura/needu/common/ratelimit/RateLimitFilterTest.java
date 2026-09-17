package kr.ktb.zura.needu.common.ratelimit;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import kr.ktb.zura.needu.common.exception.ErrorResponseWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private static final String POLICY_NAME = "personal-recommendations";
    private static final String PATH = "/api/v1/users/me/personal-recommendations";
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final long LIMIT = 2;
    private static final long MAXIMUM_SIZE = 100;
    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    private final AtomicLong nanoTime = new AtomicLong();
    private RateLimitFilter rateLimitFilter;

    @BeforeEach
    void setUp() {
        RateLimitProperties properties = new RateLimitProperties(Map.of(
                POLICY_NAME, new RateLimitProperties.Policy(HttpMethod.GET, PATH, LIMIT, WINDOW, MAXIMUM_SIZE)
        ));
        rateLimitFilter = new RateLimitFilter(
                properties, new ErrorResponseWriter(JsonMapper.builder().build()), nanoTime::get);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requestsWithinLimit_doFilter_passesToNextFilter() throws Exception {
        authenticate(USER_ID);

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
        sendRequests(PATH, "GET", LIMIT);
        nanoTime.addAndGet(Duration.ofSeconds(50).toNanos());
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
    void windowElapsed_doFilter_resetsCounter() throws Exception {
        authenticate(USER_ID);
        sendRequests(PATH, "GET", LIMIT);
        nanoTime.addAndGet(WINDOW.toNanos());
        MockFilterChain filterChain = new MockFilterChain();

        rateLimitFilter.doFilter(new MockHttpServletRequest("GET", PATH), new MockHttpServletResponse(), filterChain);

        assertThat(filterChain.getRequest()).isNotNull();
    }

    @Test
    void otherUserOverLimit_doFilter_countsSeparately() throws Exception {
        authenticate(OTHER_USER_ID);
        sendRequests(PATH, "GET", LIMIT + 1);
        authenticate(USER_ID);
        MockFilterChain filterChain = new MockFilterChain();

        rateLimitFilter.doFilter(new MockHttpServletRequest("GET", PATH), new MockHttpServletResponse(), filterChain);

        assertThat(filterChain.getRequest()).isNotNull();
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
