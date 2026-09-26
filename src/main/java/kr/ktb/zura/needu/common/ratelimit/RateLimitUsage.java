package kr.ktb.zura.needu.common.ratelimit;

import java.time.Duration;

public record RateLimitUsage(long count, Duration remaining) {
}
