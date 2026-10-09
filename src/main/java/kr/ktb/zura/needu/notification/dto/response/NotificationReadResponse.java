package kr.ktb.zura.needu.notification.dto.response;

import java.time.LocalDateTime;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationType;

public record NotificationReadResponse(
        Long notificationId,
        LocalDateTime readAt,
        NotificationType type,
        NotificationResourceType resourceType,
        Long resourceId,
        boolean targetAvailable,
        long unreadCount
) {
}
