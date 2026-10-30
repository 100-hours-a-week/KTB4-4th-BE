package kr.ktb.zura.needu.common.ratelimit;

import java.time.Duration;
import java.util.Map;

import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.common.exception.TooManyRequestsException;
import kr.ktb.zura.needu.common.response.RetryAfterResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class EventRateLimiterTest {

    private static final String EVENT_NAME = "anonymous-chat-message-send";
    private static final Long USER_ID = 1L;
    private static final long LIMIT = 10;

    private final RateLimitProperties properties = new RateLimitProperties(Map.of(), Map.of(
            EVENT_NAME, new RateLimitProperties.EventPolicy(LIMIT, Duration.ofSeconds(1))));
    private final RateLimitCounter rateLimitCounter = mock(RateLimitCounter.class);
    private final EventRateLimiter eventRateLimiter = new EventRateLimiter(properties, rateLimitCounter, true);

    @Test
    void countAtLimit_validateWithinLimit_passes() {
        given(rateLimitCounter.increment(EVENT_NAME, "1"))
                .willReturn(new RateLimitUsage(LIMIT, Duration.ofMillis(300)));

        assertThatCode(() -> eventRateLimiter.validateWithinLimit(EVENT_NAME, USER_ID))
                .doesNotThrowAnyException();
    }

    @Test
    void countOverLimit_validateWithinLimit_throwsTooManyRequestsWithRetryAfter() {
        given(rateLimitCounter.increment(EVENT_NAME, "1"))
                .willReturn(new RateLimitUsage(LIMIT + 1, Duration.ofMillis(300)));

        assertThatThrownBy(() -> eventRateLimiter.validateWithinLimit(EVENT_NAME, USER_ID))
                .isInstanceOfSatisfying(TooManyRequestsException.class, exception ->
                        assertThat(exception.getData()).isEqualTo(new RetryAfterResponse(1)));
    }

    @Test
    void redisUnavailable_validateWithinLimit_throwsServiceUnavailable() {
        given(rateLimitCounter.increment(EVENT_NAME, "1")).willThrow(new QueryTimeoutException("timeout"));

        assertThatThrownBy(() -> eventRateLimiter.validateWithinLimit(EVENT_NAME, USER_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.COMMON_SERVICE_UNAVAILABLE))
                .hasCauseInstanceOf(QueryTimeoutException.class);
    }

    @Test
    void unknownEvent_validateWithinLimit_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> eventRateLimiter.validateWithinLimit("unknown", USER_ID))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(rateLimitCounter);
    }

    @Test
    void disabled_validateWithinLimit_skipsCounting() {
        EventRateLimiter disabledLimiter = new EventRateLimiter(properties, rateLimitCounter, false);

        disabledLimiter.validateWithinLimit(EVENT_NAME, USER_ID);

        verifyNoInteractions(rateLimitCounter);
    }
}
