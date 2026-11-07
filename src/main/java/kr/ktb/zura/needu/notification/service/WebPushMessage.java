package kr.ktb.zura.needu.notification.service;

import java.time.LocalDateTime;
import java.util.List;
import kr.ktb.zura.needu.notification.type.NotificationCategory;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationType;

// 웹푸시 작업 큐 메시지다. 알림 내용이 같은 수신자를 묶어 한 메시지로 보낸다.
// 수신자마다 제목·본문을 반복하지 않아 밀린 메시지가 쌓여도 Redis 메모리를 덜 쓴다.
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

    // 수신자별 알림으로 펼친다 => WebPushClient가 수신자마다 notificationId를 담아 보낸다
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
