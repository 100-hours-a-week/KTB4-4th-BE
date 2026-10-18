package kr.ktb.zura.needu.friend.entity;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FriendSyncRequestTest {

    @Test
    void newRequest_hasImmediateFirstAttemptAndNoRetries() {
        LocalDateTime requestedAt = LocalDateTime.of(2026, 10, 14, 12, 0);

        FriendSyncRequest request = new FriendSyncRequest(1L, requestedAt);

        assertThat(request.getUserId()).isEqualTo(1L);
        assertThat(request.getRequestedAt()).isEqualTo(requestedAt);
        assertThat(request.getNextAttemptAt()).isEqualTo(requestedAt);
        assertThat(request.getRetryCount()).isZero();
    }

    @Test
    void failedAttempt_retryAt_updatesNextAttemptAndIncrementsCount() {
        FriendSyncRequest request = new FriendSyncRequest(
                1L, LocalDateTime.of(2026, 10, 14, 12, 0));
        LocalDateTime nextAttemptAt = LocalDateTime.of(2026, 10, 14, 12, 5);

        request.retryAt(nextAttemptAt);

        assertThat(request.getNextAttemptAt()).isEqualTo(nextAttemptAt);
        assertThat(request.getRetryCount()).isEqualTo(1);
    }
}
