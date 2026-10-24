package kr.ktb.zura.needu.notification.service;

import java.time.LocalDateTime;
import java.util.List;
import kr.ktb.zura.needu.notification.entity.NotificationEvent;
import kr.ktb.zura.needu.notification.type.NotificationCategory;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationType;

public record NotificationsCreatedEvent(List<CreatedNotification> notifications) {

    public NotificationsCreatedEvent {
        notifications = List.copyOf(notifications);
    }

    public record CreatedNotification(
            Long notificationId,
            Long receiverUserId,
            NotificationCategory category,
            NotificationType type,
            String title,
            String body,
            NotificationResourceType resourceType,
            Long resourceId,
            LocalDateTime createdAt
    ) {

        public static CreatedNotification of(
                NotificationEvent event,
                Long notificationId,
                Long receiverUserId,
                LocalDateTime createdAt
        ) {
            return new CreatedNotification(
                    notificationId,
                    receiverUserId,
                    event.getCategory(),
                    event.getType(),
                    event.getTitle(),
                    event.getBody(),
                    event.getResourceType(),
                    event.getResourceId(),
                    createdAt
            );
        }
    }
}
