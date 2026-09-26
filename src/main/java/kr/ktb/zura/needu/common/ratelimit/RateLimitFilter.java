package kr.ktb.zura.needu.common.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

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
    private final RateLimitCounter rateLimitCounter;
    private final ErrorResponseWriter errorResponseWriter;

    public RateLimitFilter(RateLimitProperties properties, RateLimitCounter rateLimitCounter,
                           ErrorResponseWriter errorResponseWriter) {
        this.rules = properties.policies().entrySet().stream()
                .map(entry -> RateLimitRule.from(entry.getKey(), entry.getValue()))
                .toList();
        this.rateLimitCounter = rateLimitCounter;
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
        RateLimitUsage usage = rateLimitCounter.increment(rule.get().name(), userId);
        if (usage.count() <= rule.get().limit()) {
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = toRetryAfterSeconds(usage.remaining().toMillis());
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

    private record RateLimitRule(String name, HttpMethod method, PathPattern pathPattern, long limit) {

        static RateLimitRule from(String name, RateLimitProperties.Policy policy) {
            return new RateLimitRule(
                    name,
                    policy.method(),
                    PathPatternParser.defaultInstance.parse(policy.pathPattern()),
                    policy.limit()
            );
        }

        boolean matches(String requestMethod, PathContainer path) {
            return method.matches(requestMethod) && pathPattern.matches(path);
        }
    }
}
