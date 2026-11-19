package kr.ktb.zura.needu.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import kr.ktb.zura.needu.notification.config.WebPushJobWorkerProperties;
import kr.ktb.zura.needu.notification.type.WebPushQueueType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WebPushJobWorkerManagerTest {

    private static final WebPushJobWorkerProperties.QueueProperties QUEUE =
            new WebPushJobWorkerProperties.QueueProperties(1, 1, 1, Duration.ofMillis(10));

    private final WebPushJobService jobService = mock(WebPushJobService.class);
    private final WebPushDeliveryService deliveryService = mock(WebPushDeliveryService.class);
    private WebPushJobWorkerManager manager;

    @AfterEach
    void tearDown() {
        if (manager != null) {
            manager.stop();
        }
    }

    @Test
    void allQueuesConfigured_start_pollsEveryQueue() {
        when(jobService.takeJobs(any(), anyInt())).thenReturn(List.of());
        manager = new WebPushJobWorkerManager(jobService, deliveryService, properties(Map.of(
                WebPushQueueType.REALTIME, QUEUE,
                WebPushQueueType.BULK, QUEUE)));

        manager.start();

        assertThat(manager.isRunning()).isTrue();
        verify(jobService, timeout(1_000).atLeastOnce()).takeJobs(WebPushQueueType.REALTIME, 1);
        verify(jobService, timeout(1_000).atLeastOnce()).takeJobs(WebPushQueueType.BULK, 1);
    }

    @Test
    void runningManager_stop_isNotRunning() {
        when(jobService.takeJobs(any(), anyInt())).thenReturn(List.of());
        manager = new WebPushJobWorkerManager(jobService, deliveryService, properties(Map.of(
                WebPushQueueType.REALTIME, QUEUE,
                WebPushQueueType.BULK, QUEUE)));
        manager.start();

        manager.stop();

        assertThat(manager.isRunning()).isFalse();
    }

    @Test
    void queueNotConfigured_create_throwsIllegalState() {
        assertThatThrownBy(() -> new WebPushJobWorkerManager(jobService, deliveryService,
                properties(Map.of(WebPushQueueType.REALTIME, QUEUE))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BULK");
    }

    private WebPushJobWorkerProperties properties(
            Map<WebPushQueueType, WebPushJobWorkerProperties.QueueProperties> queues
    ) {
        return new WebPushJobWorkerProperties(Duration.ofSeconds(1), queues);
    }
}
