package kr.ktb.zura.needu.common.messaging.redis;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// topic별 Redis Stream 워커 설정이다. 값은 topic마다 따로 두지 않고 모든 워커가 같이 쓴다.
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
