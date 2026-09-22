package kr.ktb.zura.needu.common.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "needu.rate-limit")
public record RateLimitProperties(@Valid Map<String, Policy> policies) {

    public RateLimitProperties {
        policies = policies == null ? Map.of() : Map.copyOf(policies);
    }

    public record Policy(
            @NotNull HttpMethod method,
            @NotBlank String pathPattern,
            @Positive long limit,
            @NotNull Duration window,
            @Positive long maximumSize
    ) {
    }
}
