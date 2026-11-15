package kr.ktb.zura.needu.notification.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import kr.ktb.zura.needu.notification.service.WebPushDeliveryService;
import kr.ktb.zura.needu.notification.service.WebPushJobService;
import kr.ktb.zura.needu.notification.service.WebPushJobWorkerManager;
import kr.ktb.zura.needu.notification.type.WebPushQueueType;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class WebPushJobWorkerConfigTest {

    private static final String[] WORKER_PROPERTIES = {
            "needu.notification.webpush-job.worker.shutdown-timeout=1s",
            "needu.notification.webpush-job.worker.queues.realtime.concurrency=8",
            "needu.notification.webpush-job.worker.queues.realtime.max-take-count=8",
            "needu.notification.webpush-job.worker.queues.realtime.send-concurrency=100",
            "needu.notification.webpush-job.worker.queues.realtime.poll-interval=200ms",
            "needu.notification.webpush-job.worker.queues.bulk.concurrency=4",
            "needu.notification.webpush-job.worker.queues.bulk.max-take-count=4",
            "needu.notification.webpush-job.worker.queues.bulk.send-concurrency=200",
            "needu.notification.webpush-job.worker.queues.bulk.poll-interval=1s"
    };

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(WebPushJobService.class, () -> mock(WebPushJobService.class))
            .withBean(WebPushDeliveryService.class, () -> mock(WebPushDeliveryService.class))
            .withUserConfiguration(WebPushJobWorkerConfig.class)
            .withPropertyValues(WORKER_PROPERTIES);

    @Test
    void allEnabled_createsWorkerManagerWithQueueProperties() {
        contextRunner
                .withPropertyValues(
                        "notification.webpush.enabled=true",
                        "needu.notification.webpush-job.enabled=true",
                        "needu.notification.webpush-job.worker.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(WebPushJobWorkerManager.class);
                    WebPushJobWorkerProperties properties = context.getBean(WebPushJobWorkerProperties.class);
                    assertThat(properties.queue(WebPushQueueType.REALTIME))
                            .isEqualTo(new WebPushJobWorkerProperties.QueueProperties(
                                    8, 8, 100, Duration.ofMillis(200)));
                    assertThat(properties.queue(WebPushQueueType.BULK))
                            .isEqualTo(new WebPushJobWorkerProperties.QueueProperties(
                                    4, 4, 200, Duration.ofSeconds(1)));
                });
    }

    @Test
    void workerDisabled_doesNotCreateWorkerManager() {
        contextRunner
                .withPropertyValues(
                        "notification.webpush.enabled=true",
                        "needu.notification.webpush-job.enabled=true",
                        "needu.notification.webpush-job.worker.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(WebPushJobWorkerManager.class));
    }

    @Test
    void jobQueueDisabled_doesNotCreateWorkerManager() {
        contextRunner
                .withPropertyValues(
                        "notification.webpush.enabled=true",
                        "needu.notification.webpush-job.enabled=false",
                        "needu.notification.webpush-job.worker.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(WebPushJobWorkerManager.class));
    }
}
