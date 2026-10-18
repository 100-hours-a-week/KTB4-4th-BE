package kr.ktb.zura.needu.notification.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PushSubscriptionKeysRequest(
        @NotBlank @Size(max = 255) String p256dh,
        @NotBlank @Size(max = 100) String auth
) {
}
