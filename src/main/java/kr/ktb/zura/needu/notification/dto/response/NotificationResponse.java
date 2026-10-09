package kr.ktb.zura.needu.notification.dto.response;

import java.time.LocalDateTime;
import kr.ktb.zura.needu.notification.type.NotificationCategory;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationType;

public record NotificationResponse(
        Long notificationId,
        NotificationCategory category,
        NotificationType type,
        String title,
        String body,
        NotificationResourceType resourceType,
        Long resourceId,
        boolean targetAvailable,
        LocalDateTime readAt,
        LocalDateTime createdAt
) {
}
