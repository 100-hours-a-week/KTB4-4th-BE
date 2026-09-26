package kr.ktb.zura.needu.common.ratelimit;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CaffeineRateLimitCounterTest {

    private static final String POLICY_NAME = "friend-list";
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final String USER_ID = "1";

    private final AtomicLong nanoTime = new AtomicLong();
    private CaffeineRateLimitCounter rateLimitCounter;

    @BeforeEach
    void setUp() {
        RateLimitProperties properties = new RateLimitProperties(Map.of(
                POLICY_NAME, new RateLimitProperties.Policy(HttpMethod.GET, "/api/v1/friends", 10, WINDOW, 100)
        ));
        rateLimitCounter = new CaffeineRateLimitCounter(properties, nanoTime::get);
    }

    @Test
    void repeatedRequests_increment_returnsCountAndRemainingWindow() {
        rateLimitCounter.increment(POLICY_NAME, USER_ID);
        nanoTime.addAndGet(Duration.ofSeconds(20).toNanos());

        RateLimitUsage usage = rateLimitCounter.increment(POLICY_NAME, USER_ID);

        assertThat(usage.count()).isEqualTo(2);
        assertThat(usage.remaining()).isEqualTo(Duration.ofSeconds(40));
    }

    @Test
    void windowElapsed_increment_startsNewCount() {
        rateLimitCounter.increment(POLICY_NAME, USER_ID);
        nanoTime.addAndGet(WINDOW.toNanos());

        RateLimitUsage usage = rateLimitCounter.increment(POLICY_NAME, USER_ID);

        assertThat(usage.count()).isEqualTo(1);
        assertThat(usage.remaining()).isEqualTo(WINDOW);
    }

    @Test
    void unknownPolicy_increment_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> rateLimitCounter.increment("unknown", USER_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
