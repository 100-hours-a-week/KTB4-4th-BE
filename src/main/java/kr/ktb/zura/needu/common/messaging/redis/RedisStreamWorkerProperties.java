package kr.ktb.zura.needu.common.messaging.redis;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "needu.messaging.stream.worker")
public record RedisStreamWorkerProperties(
        boolean enabled,
        @Positive int concurrency,
        @Positive int maxReadCount,
        @NotNull Duration pollInterval,
        @NotNull Duration shutdownTimeout
) {
}
