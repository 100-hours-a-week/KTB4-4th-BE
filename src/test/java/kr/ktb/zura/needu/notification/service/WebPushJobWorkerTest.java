package kr.ktb.zura.needu.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import kr.ktb.zura.needu.notification.config.WebPushJobWorkerProperties;
import kr.ktb.zura.needu.notification.entity.WebPushJob;
import kr.ktb.zura.needu.notification.type.NotificationCategory;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationType;
import kr.ktb.zura.needu.notification.type.WebPushQueueType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class WebPushJobWorkerTest {

    private static final WebPushQueueType QUEUE_TYPE = WebPushQueueType.REALTIME;
    private static final Duration POLL_INTERVAL = Duration.ofMillis(10);

    private final WebPushJobService jobService = mock(WebPushJobService.class);
    private final WebPushDeliveryService deliveryService = mock(WebPushDeliveryService.class);
    private WebPushJobWorker worker;

    @AfterEach
    void tearDown() {
        if (worker != null) {
            worker.stop();
        }
    }

    @Test
    void takenJob_start_deliversNotificationsAndCompletesJob() {
        worker = createWorker(2, 10, 10);
        WebPushJob job = job();
        List<NotificationsCreatedEvent.CreatedNotification> notifications = List.of(notification());
        when(jobService.takeJobs(eq(QUEUE_TYPE), anyInt())).thenReturn(List.of(job)).thenReturn(List.of());
        when(jobService.findDeliveryNotifications(job)).thenReturn(notifications);

        worker.start();

        verify(deliveryService, timeout(1_000)).deliver(eq(notifications), any(Executor.class));
        verify(jobService, timeout(1_000)).completeJob(job);
    }

    @Test
    void freeSlotsFewerThanMaxTakeCount_start_takesOnlyFreeSlotCount() {
        worker = createWorker(2, 10, 10);
        when(jobService.takeJobs(eq(QUEUE_TYPE), anyInt())).thenReturn(List.of());

        worker.start();

        verify(jobService, timeout(1_000).atLeastOnce()).takeJobs(QUEUE_TYPE, 2);
    }

    @Test
    void takeFails_start_keepsPolling() {
        worker = createWorker(1, 1, 10);
        when(jobService.takeJobs(eq(QUEUE_TYPE), anyInt()))
                .thenThrow(new DataAccessResourceFailureException("db down"))
                .thenReturn(List.of());

        worker.start();

        verify(jobService, timeout(1_000).atLeast(2)).takeJobs(eq(QUEUE_TYPE), anyInt());
    }

    @Test
    void stop_stopsPolling() throws InterruptedException {
        worker = createWorker(1, 1, 10);
        when(jobService.takeJobs(eq(QUEUE_TYPE), anyInt())).thenReturn(List.of());
        worker.start();
        verify(jobService, timeout(1_000).atLeastOnce()).takeJobs(eq(QUEUE_TYPE), anyInt());

        worker.stop();
        int callsAfterStop = mockingDetails(jobService).getInvocations().size();
        Thread.sleep(POLL_INTERVAL.multipliedBy(5).toMillis());

        assertThat(worker.isRunning()).isFalse();
        assertThat(mockingDetails(jobService).getInvocations()).hasSize(callsAfterStop);
    }

    @Test
    void deliveryFails_processJob_doesNotCompleteJob() {
        worker = createWorker(1, 1, 10);
        WebPushJob job = job();
        when(jobService.findDeliveryNotifications(job)).thenReturn(List.of(notification()));
        doThrow(new DataAccessResourceFailureException("db down"))
                .when(deliveryService).deliver(any(), any(Executor.class));

        worker.processJob(job);

        verify(jobService, never()).completeJob(any());
    }

    @Test
    void sends_processJob_runsSendsOnSendExecutorWithinSendConcurrency() throws InterruptedException {
        worker = createWorker(1, 1, 1);
        WebPushJob job = job();
        CountDownLatch sent = new CountDownLatch(2);
        when(jobService.findDeliveryNotifications(job)).thenReturn(List.of(notification()));
        doAnswer(invocation -> {
            Executor sendExecutor = invocation.getArgument(1);
            sendExecutor.execute(sent::countDown);
            // 발송 허용 수가 1이므로 앞선 발송이 끝나 허용이 돌아와야 두 번째 발송이 나간다
            sendExecutor.execute(sent::countDown);
            return null;
        }).when(deliveryService).deliver(any(), any(Executor.class));

        worker.processJob(job);

        assertThat(sent.await(1, TimeUnit.SECONDS)).isTrue();
        verify(jobService).completeJob(job);
    }

    private WebPushJobWorker createWorker(int concurrency, int maxTakeCount, int sendConcurrency) {
        return new WebPushJobWorker(
                QUEUE_TYPE,
                jobService,
                deliveryService,
                new WebPushJobWorkerProperties.QueueProperties(
                        concurrency, maxTakeCount, sendConcurrency, POLL_INTERVAL),
                Duration.ofSeconds(1));
    }

    private WebPushJob job() {
        return WebPushJob.create(100L, QUEUE_TYPE, List.of(1L), LocalDateTime.of(2026, 11, 15, 0, 0));
    }

    private NotificationsCreatedEvent.CreatedNotification notification() {
        return new NotificationsCreatedEvent.CreatedNotification(
                1L,
                10L,
                NotificationCategory.POKE,
                NotificationType.POKE,
                "친구가 콕 찔렀어요.",
                "친구의 알림을 확인해 보세요.",
                NotificationResourceType.USER,
                10L,
                LocalDateTime.of(2026, 11, 15, 0, 0)
        );
    }
}
