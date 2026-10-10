package kr.ktb.zura.needu.notification.service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;
import kr.ktb.zura.needu.notification.repository.NotificationRepository;
import kr.ktb.zura.needu.notification.repository.NotificationUnreadCount;
import kr.ktb.zura.needu.notification.sse.NotificationRedisEvent;
import kr.ktb.zura.needu.notification.type.NotificationCategory;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationCreatedEventListener {

    private final NotificationRepository notificationRepository;
    private final NotificationEventPublisher notificationEventPublisher;
    private final JsonMapper jsonMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(NotificationsCreatedEvent event) {
        Map<Long, Long> unreadCountByUserId;
        try {
            unreadCountByUserId = notificationRepository.findAllUnreadCountsByReceiverUserIdIn(
                            event.notifications().stream()
                                    .map(NotificationsCreatedEvent.CreatedNotification::receiverUserId)
                                    .toList()
                    ).stream()
                    .collect(Collectors.toMap(
                            NotificationUnreadCount::getReceiverUserId,
                            NotificationUnreadCount::getUnreadCount
                    ));
        } catch (RuntimeException exception) {
            log.warn("Notification unread count lookup failed after creation.", exception);
            return;
        }

        event.notifications().forEach(notification -> publish(
                notification,
                unreadCountByUserId.getOrDefault(notification.receiverUserId(), 0L)
        ));
    }

    private void publish(NotificationsCreatedEvent.CreatedNotification notification, long unreadCount) {
        try {
            NotificationCreatedData data = NotificationCreatedData.from(notification);
            notificationEventPublisher.publish(new NotificationRedisEvent(
                    notification.receiverUserId(),
                    jsonMapper.valueToTree(data),
                    unreadCount
            ));
        } catch (RuntimeException exception) {
            log.warn(
                    "Notification Redis publish failed. notificationId={}, receiverUserId={}",
                    notification.notificationId(),
                    notification.receiverUserId(),
                    exception
            );
        }
    }

    private record NotificationCreatedData(
            Long notificationId,
            NotificationCategory category,
            NotificationType type,
            String title,
            String body,
            NotificationResourceType resourceType,
            Long resourceId,
            boolean targetAvailable,
            LocalDateTime createdAt
    ) {

        private static NotificationCreatedData from(
                NotificationsCreatedEvent.CreatedNotification notification
        ) {
            return new NotificationCreatedData(
                    notification.notificationId(),
                    notification.category(),
                    notification.type(),
                    notification.title(),
                    notification.body(),
                    notification.resourceType(),
                    notification.resourceId(),
                    notification.resourceType() != null && notification.resourceId() != null,
                    notification.createdAt()
            );
        }
    }
}
