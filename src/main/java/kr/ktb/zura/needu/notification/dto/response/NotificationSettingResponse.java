package kr.ktb.zura.needu.notification.dto.response;

import java.time.LocalDateTime;
import kr.ktb.zura.needu.notification.entity.NotificationSetting;
import kr.ktb.zura.needu.notification.type.NotificationSettingType;

public record NotificationSettingResponse(
        NotificationSettingType type,
        boolean enabled,
        LocalDateTime updatedAt
) {

    public static NotificationSettingResponse from(
            NotificationSettingType type, NotificationSetting setting) {
        if (setting == null) {
            return new NotificationSettingResponse(type, type.isDefaultEnabled(), null);
        }
        return new NotificationSettingResponse(type, setting.isEnabled(), setting.getUpdatedAt());
    }
}
