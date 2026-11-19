package kr.ktb.zura.needu.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import kr.ktb.zura.needu.notification.type.WebPushJobStatus;
import kr.ktb.zura.needu.notification.type.WebPushQueueType;
import org.junit.jupiter.api.Test;

// RUNNING 이후 상태 변경은 일괄 UPDATE로만 RUNNING이 되므로 WebPushJobRepositoryTest에서 검증한다
class WebPushJobTest {

    private static final Long NOTIFICATION_EVENT_ID = 10L;
    private static final WebPushQueueType QUEUE_TYPE = WebPushQueueType.REALTIME;
    private static final LocalDateTime AVAILABLE_AT = LocalDateTime.of(2026, 11, 14, 0, 0);

    @Test
    void notificationIds_create_isQueuedWithoutRetryAndLease() {
        WebPushJob job = WebPushJob.create(NOTIFICATION_EVENT_ID, QUEUE_TYPE, List.of(1L, 2L), AVAILABLE_AT);

        assertThat(job.getNotificationEventId()).isEqualTo(NOTIFICATION_EVENT_ID);
        assertThat(job.getQueueType()).isEqualTo(QUEUE_TYPE);
        assertThat(job.getNotificationIds()).containsExactly(1L, 2L);
        assertThat(job.getStatus()).isEqualTo(WebPushJobStatus.QUEUED);
        assertThat(job.getRetryCount()).isZero();
        assertThat(job.getAvailableAt()).isEqualTo(AVAILABLE_AT);
        assertThat(job.getLeaseToken()).isNull();
        assertThat(job.getLeaseExpiresAt()).isNull();
    }

    @Test
    void sourceListChangedAfterCreate_getNotificationIds_keepsCreatedIds() {
        List<Long> notificationIds = new ArrayList<>(List.of(1L, 2L));
        WebPushJob job = WebPushJob.create(NOTIFICATION_EVENT_ID, QUEUE_TYPE, notificationIds, AVAILABLE_AT);

        notificationIds.add(3L);

        assertThat(job.getNotificationIds()).containsExactly(1L, 2L);
    }

    @Test
    void emptyNotificationIds_create_throwsIllegalArgument() {
        assertThatThrownBy(() -> WebPushJob.create(NOTIFICATION_EVENT_ID, QUEUE_TYPE, List.of(), AVAILABLE_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullQueueType_create_throwsIllegalArgument() {
        assertThatThrownBy(() -> WebPushJob.create(NOTIFICATION_EVENT_ID, null, List.of(1L), AVAILABLE_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void queuedJob_retry_throwsIllegalState() {
        WebPushJob job = WebPushJob.create(NOTIFICATION_EVENT_ID, QUEUE_TYPE, List.of(1L), AVAILABLE_AT);

        assertThatThrownBy(() -> job.retry(List.of(1L), AVAILABLE_AT, "WEB_PUSH_TIMEOUT"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void queuedJob_fail_throwsIllegalState() {
        WebPushJob job = WebPushJob.create(NOTIFICATION_EVENT_ID, QUEUE_TYPE, List.of(1L), AVAILABLE_AT);

        assertThatThrownBy(() -> job.fail("WEB_PUSH_TIMEOUT"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void queuedJob_isLeasedBy_returnsFalse() {
        WebPushJob job = WebPushJob.create(NOTIFICATION_EVENT_ID, QUEUE_TYPE, List.of(1L), AVAILABLE_AT);

        assertThat(job.isLeasedBy(null)).isFalse();
        assertThat(job.isLeasedBy("lease-token")).isFalse();
    }
}
