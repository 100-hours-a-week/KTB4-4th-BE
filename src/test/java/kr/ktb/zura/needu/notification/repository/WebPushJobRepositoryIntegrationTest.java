package kr.ktb.zura.needu.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import kr.ktb.zura.needu.notification.entity.WebPushJob;
import kr.ktb.zura.needu.notification.type.WebPushJobStatus;
import kr.ktb.zura.needu.notification.type.WebPushQueueType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

// FOR UPDATE SKIP LOCKED와 JSON 컬럼에 문자열로 넣는 일괄 INSERT는 H2와 동작이 달라 integration 프로필의 MySQL을 사용한다.
// 두 트랜잭션의 잠금을 함께 확인해야 하므로 테스트 트랜잭션을 쓰지 않고 직접 커밋한 뒤 지운다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WebPushJobRepositoryIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 14, 0, 0);
    private static final WebPushQueueType REALTIME = WebPushQueueType.REALTIME;
    private static final WebPushQueueType BULK = WebPushQueueType.BULK;

    @Autowired
    private WebPushJobRepository webPushJobRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void tearDown() {
        webPushJobRepository.deleteAllInBatch();
    }

    @Test
    void rows_insertAll_savesQueuedJobsReadableAsEntities() {
        transaction().executeWithoutResult(status -> webPushJobRepository.insertAll(List.of(
                new WebPushJobInsertRow(10L, BULK, List.of(3L, 1L, 2L)),
                new WebPushJobInsertRow(20L, REALTIME, List.of(4L))
        ), NOW));

        List<WebPushJob> jobs = webPushJobRepository.findAll();

        assertThat(jobs).extracting(WebPushJob::getNotificationEventId).containsExactlyInAnyOrder(10L, 20L);
        WebPushJob job = jobs.stream().filter(found -> found.getNotificationEventId().equals(10L)).findFirst()
                .orElseThrow();
        assertThat(job.getQueueType()).isEqualTo(BULK);
        assertThat(job.getNotificationIds()).containsExactly(3L, 1L, 2L);
        assertThat(job.getStatus()).isEqualTo(WebPushJobStatus.QUEUED);
        assertThat(job.getRetryCount()).isZero();
        assertThat(job.getAvailableAt()).isEqualTo(NOW);
        assertThat(job.getCreatedAt()).isEqualTo(NOW);
        assertThat(job.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void mixedJobs_findAllAvailableIdsForUpdate_returnsAvailableQueuedIdsOfQueueInAvailableOrderUpToLimit() {
        Long later = save(WebPushJob.create(1L, REALTIME, List.of(1L), NOW.minusSeconds(10)));
        Long earliest = save(WebPushJob.create(2L, REALTIME, List.of(2L), NOW.minusSeconds(30)));
        Long middle = save(WebPushJob.create(3L, REALTIME, List.of(3L), NOW.minusSeconds(20)));
        save(WebPushJob.create(4L, REALTIME, List.of(4L), NOW.plusSeconds(10)));
        Long bulk = save(WebPushJob.create(5L, BULK, List.of(5L), NOW.minusSeconds(50)));
        Long running = save(WebPushJob.create(6L, REALTIME, List.of(6L), NOW.minusSeconds(40)));
        transaction().executeWithoutResult(status ->
                webPushJobRepository.updateToRunning(List.of(running), "lease-token", NOW.plusMinutes(5), NOW));

        List<Long> allIds = transaction().execute(status ->
                webPushJobRepository.findAllAvailableIdsForUpdate(REALTIME, NOW, 10));
        List<Long> limitedIds = transaction().execute(status ->
                webPushJobRepository.findAllAvailableIdsForUpdate(REALTIME, NOW, 2));
        List<Long> bulkIds = transaction().execute(status ->
                webPushJobRepository.findAllAvailableIdsForUpdate(BULK, NOW, 10));

        assertThat(allIds).containsExactly(earliest, middle, later);
        assertThat(limitedIds).containsExactly(earliest, middle);
        assertThat(bulkIds).containsExactly(bulk);
    }

    @Test
    void jobsLockedByOtherTransaction_findAllAvailableIdsForUpdate_skipsLockedJobs() throws Exception {
        List<Long> ids = List.of(
                save(WebPushJob.create(1L, REALTIME, List.of(1L), NOW.minusSeconds(30))),
                save(WebPushJob.create(2L, REALTIME, List.of(2L), NOW.minusSeconds(20))),
                save(WebPushJob.create(3L, REALTIME, List.of(3L), NOW.minusSeconds(10))));
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<List<Long>> firstWorker = executor.submit(() -> transaction().execute(status -> {
                List<Long> lockedIds = webPushJobRepository.findAllAvailableIdsForUpdate(REALTIME, NOW, 2);
                locked.countDown();
                awaitQuietly(release);
                return lockedIds;
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            List<Long> secondWorkerIds = transaction().execute(status ->
                    webPushJobRepository.findAllAvailableIdsForUpdate(REALTIME, NOW, 10));
            release.countDown();

            assertThat(firstWorker.get(10, TimeUnit.SECONDS)).containsExactly(ids.get(0), ids.get(1));
            assertThat(secondWorkerIds).containsExactly(ids.get(2));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private Long save(WebPushJob job) {
        return transaction().execute(status -> webPushJobRepository.saveAndFlush(job).getId());
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
