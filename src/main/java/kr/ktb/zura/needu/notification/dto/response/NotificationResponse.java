package kr.ktb.zura.needu.notification.dto.response;

import java.time.LocalDateTime;
import kr.ktb.zura.needu.notification.type.NotificationCategory;
import kr.ktb.zura.needu.notification.type.NotificationTargetType;

public record NotificationResponse(
        Long notificationId,
        NotificationCategory category,
        String title,
        String body,
        NotificationTargetType targetType,
        String targetId,
        boolean targetAvailable,
        LocalDateTime readAt,
        LocalDateTime createdAt
) {
}
