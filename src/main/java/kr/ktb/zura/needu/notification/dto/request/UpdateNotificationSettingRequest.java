package kr.ktb.zura.needu.notification.dto.request;

import jakarta.validation.constraints.NotNull;

public record UpdateNotificationSettingRequest(
        @NotNull Boolean enabled
) {
}
