package kr.ktb.zura.needu.notification.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DeletePushSubscriptionRequest(
        @NotBlank @Size(max = 1000) @Pattern(regexp = "^https://.+") String endpoint
) {
}
