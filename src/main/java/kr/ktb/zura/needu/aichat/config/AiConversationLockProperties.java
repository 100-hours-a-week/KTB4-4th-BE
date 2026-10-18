package kr.ktb.zura.needu.aichat.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "ai.chat.lock")
public record AiConversationLockProperties(
        @NotNull Duration ttl,
        @NotBlank String keyPrefix
) {
}
