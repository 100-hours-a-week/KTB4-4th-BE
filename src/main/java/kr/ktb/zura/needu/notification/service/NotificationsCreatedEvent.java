package kr.ktb.zura.needu.notification.service;

import java.time.LocalDateTime;
import java.util.List;
import kr.ktb.zura.needu.notification.entity.Notification;
import kr.ktb.zura.needu.notification.type.NotificationCategory;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationType;

public record NotificationsCreatedEvent(List<CreatedNotification> notifications) {

    public NotificationsCreatedEvent {
        notifications = List.copyOf(notifications);
    }

    public static NotificationsCreatedEvent from(List<Notification> notifications) {
        return new NotificationsCreatedEvent(notifications.stream()
                .map(CreatedNotification::from)
                .toList());
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

        private static CreatedNotification from(Notification notification) {
            return new CreatedNotification(
                    notification.getId(),
                    notification.getReceiverUserId(),
                    notification.getEvent().getCategory(),
                    notification.getEvent().getType(),
                    notification.getEvent().getTitle(),
                    notification.getEvent().getBody(),
                    notification.getEvent().getResourceType(),
                    notification.getEvent().getResourceId(),
                    notification.getCreatedAt()
            );
        }
    }
}
