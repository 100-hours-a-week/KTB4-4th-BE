package kr.ktb.zura.needu.notification.type;

import lombok.Getter;

@Getter
public enum NotificationType {
    CHAT(NotificationCategory.CHAT, NotificationSettingType.CHAT),
    POKE(NotificationCategory.POKE, NotificationSettingType.POKE),
    FRIEND_BIRTHDAY(NotificationCategory.EVENT, NotificationSettingType.FRIEND_BIRTHDAY),
    FRIEND_JOINED(NotificationCategory.FRIEND_JOINED, NotificationSettingType.FRIEND_JOINED),
    PURCHASE_STATUS(NotificationCategory.PURCHASE_STATUS, NotificationSettingType.PURCHASE_STATUS);

    private final NotificationCategory category;
    private final NotificationSettingType settingType;

    NotificationType(NotificationCategory category, NotificationSettingType settingType) {
        this.category = category;
        this.settingType = settingType;
    }
}
