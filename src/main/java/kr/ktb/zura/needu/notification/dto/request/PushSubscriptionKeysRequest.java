package kr.ktb.zura.needu.notification.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PushSubscriptionKeysRequest(
        @NotBlank String p256dh,
        @NotBlank String auth
) {
}
