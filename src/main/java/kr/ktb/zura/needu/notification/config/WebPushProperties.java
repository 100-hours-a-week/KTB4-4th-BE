package kr.ktb.zura.needu.notification.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "notification.webpush")
public record WebPushProperties(
        @NotBlank String publicKey,
        @NotBlank String privateKey,
        @NotBlank String subject,
        @NotNull Duration ttl,
        @NotNull Duration requestTimeout
) {

    public WebPushProperties {
        if (ttl != null && (ttl.isZero() || ttl.isNegative())) {
            throw new IllegalArgumentException("Web push TTL must be positive");
        }
        if (requestTimeout != null && (requestTimeout.isZero() || requestTimeout.isNegative())) {
            throw new IllegalArgumentException("Web push request timeout must be positive");
        }
    }
}
