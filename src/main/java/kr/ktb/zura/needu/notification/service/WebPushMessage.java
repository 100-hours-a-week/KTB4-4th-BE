package kr.ktb.zura.needu.notification.service;

import java.time.LocalDateTime;
import java.util.List;
import kr.ktb.zura.needu.notification.type.NotificationCategory;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationType;

public record WebPushMessage(
        NotificationCategory category,
        NotificationType type,
        String title,
        String body,
        NotificationResourceType resourceType,
        Long resourceId,
        LocalDateTime createdAt,
        List<Recipient> recipients
) {

    public static final String TOPIC = "webpush";

    public WebPushMessage {
        recipients = recipients == null ? List.of() : List.copyOf(recipients);
    }

    public List<NotificationsCreatedEvent.CreatedNotification> toNotifications() {
        return recipients.stream()
                .map(recipient -> new NotificationsCreatedEvent.CreatedNotification(
                        recipient.notificationId(),
                        recipient.receiverUserId(),
                        category,
                        type,
                        title,
                        body,
                        resourceType,
                        resourceId,
                        createdAt))
                .toList();
    }

    public record Recipient(Long notificationId, Long receiverUserId) {
    }
}
