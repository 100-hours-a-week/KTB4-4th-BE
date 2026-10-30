package kr.ktb.zura.needu.common.ratelimit;

import java.time.Duration;

public record RateLimitUsage(long count, Duration remaining) {

    private static final long MILLIS_PER_SECOND = 1_000L;
    private static final long MIN_RETRY_AFTER_SECONDS = 1L;

    public long retryAfterSeconds() {
        long seconds = (remaining.toMillis() + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND;
        return Math.max(MIN_RETRY_AFTER_SECONDS, seconds);
    }
}
