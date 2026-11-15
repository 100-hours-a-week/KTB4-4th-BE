package kr.ktb.zura.needu.notification.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.Map;
import kr.ktb.zura.needu.notification.type.WebPushQueueType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "needu.notification.webpush-job.worker")
public record WebPushJobWorkerProperties(
        @NotNull Duration shutdownTimeout,
        @NotNull Map<WebPushQueueType, @Valid @NotNull QueueProperties> queues
) {

    public QueueProperties queue(WebPushQueueType queueType) {
        QueueProperties queue = queues.get(queueType);
        if (queue == null) {
            throw new IllegalStateException(
                    "Web push job worker queue is not configured. queueType=" + queueType);
        }
        return queue;
    }

    public record QueueProperties(
            @Positive int concurrency,
            @Positive int maxTakeCount,
            @Positive int sendConcurrency,
            @NotNull Duration pollInterval
    ) {
    }
}
