package kr.ktb.zura.needu.common.ratelimit;

import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.common.exception.TooManyRequestsException;
import org.springframework.dao.DataAccessException;

public class EventRateLimiter {

    private final RateLimitProperties properties;
    private final RateLimitCounter rateLimitCounter;
    private final boolean isEnabled;

    public EventRateLimiter(RateLimitProperties properties, RateLimitCounter rateLimitCounter, boolean isEnabled) {
        this.properties = properties;
        this.rateLimitCounter = rateLimitCounter;
        this.isEnabled = isEnabled;
    }

    public void validateWithinLimit(String eventName, Long userId) {
        if (!isEnabled) {
            return;
        }
        RateLimitProperties.EventPolicy policy = properties.events().get(eventName);
        if (policy == null) {
            throw new IllegalArgumentException("Unknown rate limit event: " + eventName);
        }

        RateLimitUsage usage;
        try {
            usage = rateLimitCounter.increment(eventName, String.valueOf(userId));
        } catch (DataAccessException exception) {
            throw new BusinessException(CommonErrorCode.COMMON_SERVICE_UNAVAILABLE, null, exception);
        }
        if (usage.count() > policy.limit()) {
            throw new TooManyRequestsException(usage.retryAfterSeconds());
        }
    }
}
