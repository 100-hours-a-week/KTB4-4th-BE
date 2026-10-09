package kr.ktb.zura.needu.notification.type;

import lombok.Getter;

@Getter
public enum NotificationType {
    CHAT(NotificationCategory.CHAT),
    POKE(NotificationCategory.POKE),
    FRIEND_BIRTHDAY(NotificationCategory.EVENT),
    FRIEND_JOINED(NotificationCategory.FRIEND_JOINED),
    PURCHASE_STATUS(NotificationCategory.PURCHASE_STATUS);

    private final NotificationCategory category;

    NotificationType(NotificationCategory category) {
        this.category = category;
    }
}
