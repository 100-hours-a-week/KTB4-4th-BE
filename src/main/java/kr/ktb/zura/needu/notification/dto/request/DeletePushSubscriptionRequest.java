package kr.ktb.zura.needu.notification.dto.request;

import jakarta.validation.constraints.NotBlank;

public record DeletePushSubscriptionRequest(@NotBlank String endpoint) {
}
