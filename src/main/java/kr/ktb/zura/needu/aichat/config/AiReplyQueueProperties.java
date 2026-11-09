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
        @Positive int globalMaxConcurrency,
        @NotNull Duration permitTtl,
        @Positive int maxDeliveries,
        @NotNull Duration heartbeatInterval,
        @NotNull Duration claimMinIdle,
        @NotNull Duration claimInterval,
        @NotNull Duration pollInterval,
        @NotNull Duration turnInProgressRetryDelay
) {
}
