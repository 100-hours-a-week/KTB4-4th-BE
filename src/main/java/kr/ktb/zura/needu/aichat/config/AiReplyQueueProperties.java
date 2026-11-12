package kr.ktb.zura.needu.aichat.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "ai.chat.reply-queue")
public record AiReplyQueueProperties(
        boolean enabled,
        boolean workerEnabled,
        @Positive int globalMaxConcurrency,
        @NotNull Duration permitTtl,
        @Positive int maxDeliveries,
        @NotNull Duration heartbeatInterval,
        @NotNull Duration claimMinIdle,
        @NotNull Duration claimInterval,
        @NotNull Duration pollInterval,
        @NotNull Duration turnInProgressRetryDelay
) {

    // 큐를 끄면 API가 작업을 스트림에 넣지 않는다 (워커만 켜면 받을 작업이 없음)
    public AiReplyQueueProperties {
        if (workerEnabled && !enabled) {
            throw new IllegalArgumentException(
                    "ai.chat.reply-queue.worker-enabled requires ai.chat.reply-queue.enabled=true");
        }
    }
}
