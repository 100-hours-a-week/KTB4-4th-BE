package kr.ktb.zura.needu.common.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.common.exception.ErrorResponseWriter;
import kr.ktb.zura.needu.common.response.RetryAfterResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long MILLIS_PER_SECOND = 1_000L;
    private static final long MIN_RETRY_AFTER_SECONDS = 1L;

    private final List<RateLimitRule> rules;
    private final ErrorResponseWriter errorResponseWriter;

    public RateLimitFilter(RateLimitProperties properties, ErrorResponseWriter errorResponseWriter) {
        this(properties, errorResponseWriter, Ticker.systemTicker());
    }

    RateLimitFilter(RateLimitProperties properties, ErrorResponseWriter errorResponseWriter, Ticker ticker) {
        this.rules = properties.policies().entrySet().stream()
                .map(entry -> RateLimitRule.from(entry.getKey(), entry.getValue(), ticker))
                .toList();
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Optional<RateLimitRule> rule = findRule(request);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (rule.isEmpty() || !isAuthenticatedUser(authentication)) {
            filterChain.doFilter(request, response);
            return;
        }

        String userId = authentication.getName();
        if (rule.get().increment(userId) <= rule.get().limit()) {
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = toRetryAfterSeconds(rule.get().findRemainingMillis(userId));
        log.info("Rate limit exceeded. policy={}, userId={}", rule.get().name(), userId);
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        errorResponseWriter.write(response, CommonErrorCode.COMMON_TOO_MANY_REQUESTS,
                new RetryAfterResponse(retryAfterSeconds));
    }

    private Optional<RateLimitRule> findRule(HttpServletRequest request) {
        PathContainer path = PathContainer.parsePath(request.getRequestURI());
        return rules.stream()
                .filter(rule -> rule.matches(request.getMethod(), path))
                .findFirst();
    }

    private boolean isAuthenticatedUser(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private long toRetryAfterSeconds(long remainingMillis) {
        long seconds = (remainingMillis + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND;
        return Math.max(MIN_RETRY_AFTER_SECONDS, seconds);
    }

    private record RateLimitRule(
            String name,
            HttpMethod method,
            PathPattern pathPattern,
            long limit,
            Duration window,
            Cache<String, AtomicLong> counters
    ) {

        static RateLimitRule from(String name, RateLimitProperties.Policy policy, Ticker ticker) {
            // 카운터 값만 증가시키고 캐시에 다시 쓰지 않으므로, 첫 요청 시점부터 window가 지나면 만료되는 고정 윈도우가 된다.
            // maximumSize로 사용자 수가 몰려도 메모리 사용량이 일정 크기를 넘지 않게 한다.
            Cache<String, AtomicLong> counters = Caffeine.newBuilder()
                    .expireAfterWrite(policy.window())
                    .maximumSize(policy.maximumSize())
                    .ticker(ticker)
                    .build();
            return new RateLimitRule(
                    name,
                    policy.method(),
                    PathPatternParser.defaultInstance.parse(policy.pathPattern()),
                    policy.limit(),
                    policy.window(),
                    counters
            );
        }

        boolean matches(String requestMethod, PathContainer path) {
            return method.matches(requestMethod) && pathPattern.matches(path);
        }

        long increment(String userId) {
            return counters.get(userId, key -> new AtomicLong()).incrementAndGet();
        }

        long findRemainingMillis(String userId) {
            Duration age = counters.policy().expireAfterWrite()
                    .flatMap(expiration -> expiration.ageOf(userId))
                    .orElse(Duration.ZERO);
            return window.minus(age).toMillis();
        }
    }
}
