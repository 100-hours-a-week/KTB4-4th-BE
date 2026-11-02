package kr.ktb.zura.needu.notification.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;
import kr.ktb.zura.needu.notification.entity.NotificationEvent;
import kr.ktb.zura.needu.notification.type.NotificationCategory;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationType;

public record NotificationsCreatedEvent(List<CreatedNotification> notifications) {

    public NotificationsCreatedEvent {
        notifications = List.copyOf(notifications);
    }

    /**
     * @param notificationIds 일괄 INSERT로 생성된 알림 ID, {@code receiverUserIds}와 같은 순서
     */
    public static NotificationsCreatedEvent of(
            NotificationEvent event,
            List<Long> notificationIds,
            List<Long> receiverUserIds,
            LocalDateTime createdAt
    ) {
        if (notificationIds.size() != receiverUserIds.size()) {
            throw new IllegalArgumentException("Notification id count must match receiver count. ids="
                    + notificationIds.size() + ", receivers=" + receiverUserIds.size());
        }
        return new NotificationsCreatedEvent(IntStream.range(0, notificationIds.size())
                .mapToObj(index -> CreatedNotification.of(
                        event, notificationIds.get(index), receiverUserIds.get(index), createdAt))
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

        private static CreatedNotification of(
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
