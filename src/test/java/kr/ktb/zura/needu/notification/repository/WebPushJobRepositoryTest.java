package kr.ktb.zura.needu.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import kr.ktb.zura.needu.notification.entity.WebPushJob;
import kr.ktb.zura.needu.notification.type.WebPushJobStatus;
import kr.ktb.zura.needu.notification.type.WebPushQueueType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Limit;

// FOR UPDATE SKIP LOCKED 조회와 JDBC 일괄 INSERT는 MySQL이 필요해 WebPushJobRepositoryIntegrationTest에서 검증한다
@DataJpaTest
class WebPushJobRepositoryTest {

    private static final Long NOTIFICATION_EVENT_ID = 10L;
    private static final String LEASE_TOKEN = "lease-token-1";
    private static final String OTHER_LEASE_TOKEN = "lease-token-2";
    private static final String ERROR_CODE = "WEB_PUSH_TIMEOUT";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 14, 0, 0);
    private static final LocalDateTime LEASE_EXPIRES_AT = NOW.plusMinutes(5);

    @Autowired
    private WebPushJobRepository webPushJobRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void jobSaved_findById_returnsNotificationIdsAndTimestamps() {
        WebPushJob saved = webPushJobRepository.saveAndFlush(queuedJob(List.of(3L, 1L, 2L)));
        entityManager.clear();

        WebPushJob found = webPushJobRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getNotificationEventId()).isEqualTo(NOTIFICATION_EVENT_ID);
        assertThat(found.getQueueType()).isEqualTo(WebPushQueueType.BULK);
        assertThat(found.getNotificationIds()).containsExactly(3L, 1L, 2L);
        assertThat(found.getStatus()).isEqualTo(WebPushJobStatus.QUEUED);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void queuedAndFailedJobs_updateToRunning_leasesOnlyQueuedJobs() {
        WebPushJob queued = webPushJobRepository.saveAndFlush(queuedJob(List.of(1L)));
        WebPushJob failed = webPushJobRepository.saveAndFlush(failedJob(List.of(2L)));

        int updated = webPushJobRepository.updateToRunning(
                List.of(queued.getId(), failed.getId()), LEASE_TOKEN, LEASE_EXPIRES_AT, NOW);

        assertThat(updated).isEqualTo(1);
        WebPushJob running = webPushJobRepository.findById(queued.getId()).orElseThrow();
        assertThat(running.getStatus()).isEqualTo(WebPushJobStatus.RUNNING);
        assertThat(running.isLeasedBy(LEASE_TOKEN)).isTrue();
        assertThat(running.getLeaseExpiresAt()).isEqualTo(LEASE_EXPIRES_AT);
        assertThat(running.getUpdatedAt()).isEqualTo(NOW);
        assertThat(webPushJobRepository.findById(failed.getId()).orElseThrow().getStatus())
                .isEqualTo(WebPushJobStatus.FAILED);
    }

    @Test
    void runningJob_findByIdAndLeaseTokenForUpdate_returnsOnlyWithMatchingToken() {
        WebPushJob running = runningJob(List.of(1L));

        assertThat(webPushJobRepository.findByIdAndLeaseTokenForUpdate(running.getId(), LEASE_TOKEN)).isPresent();
        assertThat(webPushJobRepository.findByIdAndLeaseTokenForUpdate(running.getId(), OTHER_LEASE_TOKEN)).isEmpty();
    }

    @Test
    void runningJob_retry_requeuesRemainingIdsWithRetryCountAndReleasedLease() {
        WebPushJob running = runningJob(List.of(1L, 2L, 3L));
        LocalDateTime retryAt = NOW.plusSeconds(30);

        WebPushJob job = webPushJobRepository.findByIdAndLeaseTokenForUpdate(running.getId(), LEASE_TOKEN)
                .orElseThrow();
        job.retry(List.of(2L), retryAt, ERROR_CODE);
        entityManager.flush();
        entityManager.clear();

        WebPushJob found = webPushJobRepository.findById(running.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(WebPushJobStatus.QUEUED);
        assertThat(found.getNotificationIds()).containsExactly(2L);
        assertThat(found.getRetryCount()).isEqualTo(1);
        assertThat(found.getAvailableAt()).isEqualTo(retryAt);
        assertThat(found.getLastErrorCode()).isEqualTo(ERROR_CODE);
        assertThat(found.getLeaseToken()).isNull();
        assertThat(found.getLeaseExpiresAt()).isNull();
    }

    @Test
    void runningJob_fail_becomesFailedWithReleasedLease() {
        WebPushJob running = runningJob(List.of(1L));

        WebPushJob job = webPushJobRepository.findByIdAndLeaseTokenForUpdate(running.getId(), LEASE_TOKEN)
                .orElseThrow();
        job.fail(ERROR_CODE);
        entityManager.flush();
        entityManager.clear();

        WebPushJob found = webPushJobRepository.findById(running.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(WebPushJobStatus.FAILED);
        assertThat(found.getLastErrorCode()).isEqualTo(ERROR_CODE);
        assertThat(found.getLeaseToken()).isNull();
        assertThat(found.getLeaseExpiresAt()).isNull();
    }

    @Test
    void runningJob_deleteByIdAndLeaseToken_deletesOnlyWithMatchingToken() {
        WebPushJob running = runningJob(List.of(1L));

        assertThat(webPushJobRepository.deleteByIdAndLeaseToken(running.getId(), OTHER_LEASE_TOKEN)).isZero();
        assertThat(webPushJobRepository.existsById(running.getId())).isTrue();

        assertThat(webPushJobRepository.deleteByIdAndLeaseToken(running.getId(), LEASE_TOKEN)).isEqualTo(1);
        entityManager.clear();
        assertThat(webPushJobRepository.existsById(running.getId())).isFalse();
    }

    @Test
    void expiredAndActiveLeases_findAllByStatusAndLeaseExpiresAtLessThanEqual_returnsOnlyExpired() {
        WebPushJob expired = runningJob(List.of(1L));
        WebPushJob active = webPushJobRepository.saveAndFlush(queuedJob(List.of(2L)));
        webPushJobRepository.updateToRunning(List.of(active.getId()), OTHER_LEASE_TOKEN, NOW.plusMinutes(10), NOW);

        List<WebPushJob> found = webPushJobRepository.findAllByStatusAndLeaseExpiresAtLessThanEqual(
                WebPushJobStatus.RUNNING, LEASE_EXPIRES_AT, Limit.of(10));

        assertThat(found).extracting(WebPushJob::getId).containsExactly(expired.getId());
    }

    @Test
    void failedAndQueuedJobs_deleteAllByStatusAndUpdatedAtBefore_deletesOnlyOldFailedJobs() {
        WebPushJob failed = webPushJobRepository.saveAndFlush(failedJob(List.of(1L)));
        WebPushJob queued = webPushJobRepository.saveAndFlush(queuedJob(List.of(2L)));
        LocalDateTime beforeUpdate = failed.getUpdatedAt().minusSeconds(1);
        LocalDateTime afterUpdate = failed.getUpdatedAt().plusSeconds(1);

        assertThat(webPushJobRepository.deleteAllByStatusAndUpdatedAtBefore(WebPushJobStatus.FAILED, beforeUpdate))
                .isZero();
        assertThat(webPushJobRepository.deleteAllByStatusAndUpdatedAtBefore(WebPushJobStatus.FAILED, afterUpdate))
                .isEqualTo(1);
        entityManager.clear();
        assertThat(webPushJobRepository.existsById(failed.getId())).isFalse();
        assertThat(webPushJobRepository.existsById(queued.getId())).isTrue();
    }

    private WebPushJob queuedJob(List<Long> notificationIds) {
        return WebPushJob.create(NOTIFICATION_EVENT_ID, WebPushQueueType.BULK, notificationIds, NOW);
    }

    private WebPushJob runningJob(List<Long> notificationIds) {
        WebPushJob saved = webPushJobRepository.saveAndFlush(queuedJob(notificationIds));
        webPushJobRepository.updateToRunning(List.of(saved.getId()), LEASE_TOKEN, LEASE_EXPIRES_AT, NOW);
        return webPushJobRepository.findById(saved.getId()).orElseThrow();
    }

    private WebPushJob failedJob(List<Long> notificationIds) {
        WebPushJob running = runningJob(notificationIds);
        running.fail(ERROR_CODE);
        return webPushJobRepository.saveAndFlush(running);
    }
}
