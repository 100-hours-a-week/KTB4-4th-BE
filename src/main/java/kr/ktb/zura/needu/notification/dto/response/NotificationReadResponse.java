package kr.ktb.zura.needu.notification.dto.response;

import java.time.LocalDateTime;
import kr.ktb.zura.needu.notification.entity.Notification;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;

public record NotificationReadResponse(
        Long notificationId,
        LocalDateTime readAt,
        NotificationResourceType resourceType,
        Long resourceId,
        boolean targetAvailable,
        long unreadCount
) {

    public static NotificationReadResponse from(
            Notification notification,
            boolean targetAvailable,
            long unreadCount
    ) {
        return new NotificationReadResponse(
                notification.getId(),
                notification.getReadAt(),
                notification.getEvent().getResourceType(),
                notification.getEvent().getResourceId(),
                targetAvailable,
                unreadCount
        );
    }
}
