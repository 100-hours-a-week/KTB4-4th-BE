package kr.ktb.zura.needu.common.redis;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// AI 대화·대량 알림 스트림용 Redis(ElastiCache Valkey) 접속 설정이다. 캐싱용은 spring.data.redis.*를 쓴다.
@Validated
@ConfigurationProperties(prefix = "needu.stream-redis")
public record StreamRedisProperties(
        @NotBlank String host,
        @Positive int port,
        @PositiveOrZero int database,
        String username,
        String password,
        boolean sslEnabled,
        @NotNull Duration connectTimeout,
        @NotNull Duration timeout
) {
}
