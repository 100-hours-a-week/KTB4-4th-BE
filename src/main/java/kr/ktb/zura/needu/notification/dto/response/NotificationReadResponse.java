package kr.ktb.zura.needu.notification.dto.response;

import java.time.LocalDateTime;
import kr.ktb.zura.needu.notification.type.NotificationTargetType;

public record NotificationReadResponse(
        Long notificationId,
        LocalDateTime readAt,
        NotificationTargetType targetType,
        String targetId,
        boolean targetAvailable,
        long unreadCount
) {
}
