package kr.ktb.zura.needu.notification.type;

import lombok.Getter;

@Getter
public enum NotificationType {
    CHAT(NotificationCategory.CHAT, NotificationSettingType.CHAT, WebPushQueueType.REALTIME),
    POKE(NotificationCategory.POKE, NotificationSettingType.POKE, WebPushQueueType.REALTIME),
    FRIEND_BIRTHDAY(NotificationCategory.EVENT, NotificationSettingType.FRIEND_BIRTHDAY, WebPushQueueType.BULK),
    FRIEND_JOINED(NotificationCategory.FRIEND_JOINED, NotificationSettingType.FRIEND_JOINED, WebPushQueueType.REALTIME),
    PURCHASE_STATUS(NotificationCategory.PURCHASE_STATUS, NotificationSettingType.PURCHASE_FEEDBACK, WebPushQueueType.BULK);

    private final NotificationCategory category;
    private final NotificationSettingType settingType;
    private final WebPushQueueType webPushQueueType;

    NotificationType(
            NotificationCategory category,
            NotificationSettingType settingType,
            WebPushQueueType webPushQueueType
    ) {
        this.category = category;
        this.settingType = settingType;
        this.webPushQueueType = webPushQueueType;
    }
}
