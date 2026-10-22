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
    private final NotificationPreviewService notificationPreviewService;
    private final JsonMapper jsonMapper;
    private final NotificationStageTimer stageTimer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(NotificationsCreatedEvent event) {
        if (event.notifications().isEmpty()) {
            return;
        }

        NotificationType type = event.notifications().getFirst().type();
        Map<Long, Long> unreadCountByUserId;
        try {
            unreadCountByUserId = stageTimer.time(type, NotificationStage.UNREAD_LOOKUP,
                            () -> notificationRepository.findAllUnreadCountsByReceiverUserIdIn(
                                    event.notifications().stream()
                                            .map(NotificationsCreatedEvent.CreatedNotification::receiverUserId)
                                            .toList()
                            )).stream()
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
            NotificationPreview preview = stageTimer.time(notification.type(), NotificationStage.PREVIEW_LOOKUP,
                    () -> notificationPreviewService.findPreview(
                            notification.receiverUserId(), notification.resourceType(), notification.resourceId()));
            NotificationCreatedData data = NotificationCreatedData.from(notification, preview);
            stageTimer.run(notification.type(), NotificationStage.PUBLISH,
                    () -> notificationEventPublisher.publish(new NotificationRedisEvent(
                            notification.receiverUserId(),
                            jsonMapper.valueToTree(data),
                            unreadCount
                    )));
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
            NotificationPreview preview,
            LocalDateTime createdAt
    ) {

        private static NotificationCreatedData from(
                NotificationsCreatedEvent.CreatedNotification notification,
                NotificationPreview preview
        ) {
            return new NotificationCreatedData(
                    notification.notificationId(),
                    notification.category(),
                    notification.type(),
                    notification.title(),
                    notification.body(),
                    notification.resourceType(),
                    notification.resourceId(),
                    notification.type() == NotificationType.POKE
                            || (notification.resourceType() != null && notification.resourceId() != null),
                    preview,
                    notification.createdAt()
            );
        }
    }
}
