package kr.ktb.zura.needu.aichat.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "ai.chat.reply-queue")
public record AiReplyQueueProperties(
        @Positive int globalMaxConcurrency,
        @NotNull Duration permitTtl
) {
}
