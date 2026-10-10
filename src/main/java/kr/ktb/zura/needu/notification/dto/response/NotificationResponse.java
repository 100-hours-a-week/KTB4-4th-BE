package kr.ktb.zura.needu.notification.dto.response;

import java.time.LocalDateTime;
import kr.ktb.zura.needu.notification.entity.Notification;
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

    public static NotificationResponse from(Notification notification, boolean targetAvailable) {
        return new NotificationResponse(
                notification.getId(),
                notification.getEvent().getCategory(),
                notification.getEvent().getType(),
                notification.getEvent().getTitle(),
                notification.getEvent().getBody(),
                notification.getEvent().getResourceType(),
                notification.getEvent().getResourceId(),
                targetAvailable,
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }
}
