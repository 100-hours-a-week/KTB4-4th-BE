package kr.ktb.zura.needu.notification.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import kr.ktb.zura.needu.notification.repository.NotificationRepository;
import kr.ktb.zura.needu.notification.repository.NotificationUnreadCount;
import kr.ktb.zura.needu.notification.service.NotificationsCreatedEvent.CreatedNotification;
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
    private final NotificationPresenceRegistry presenceRegistry;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(NotificationsCreatedEvent event) {
        List<CreatedNotification> onlineNotifications = filterOnlineReceivers(event.notifications());
        if (onlineNotifications.isEmpty()) {
            return;
        }

        Map<Long, Long> unreadCountByUserId;
        try {
            unreadCountByUserId = findUnreadCounts(onlineNotifications);
        } catch (RuntimeException exception) {
            log.warn("Notification unread count lookup failed after creation.", exception);
            return;
        }

        onlineNotifications.forEach(notification -> publish(
                notification,
                unreadCountByUserId.getOrDefault(notification.receiverUserId(), 0L)
        ));
    }

    // SSE 접속이 없는 수신자는 저장된 알림을 목록에서 보므로 실시간 전송을 건너뛴다
    private List<CreatedNotification> filterOnlineReceivers(List<CreatedNotification> notifications) {
        if (notifications.isEmpty()) {
            return List.of();
        }
        Set<Long> onlineUserIds = stageTimer.time(notifications.getFirst().type(), NotificationStage.PRESENCE_LOOKUP,
                () -> presenceRegistry.findOnlineUserIds(receiverUserIds(notifications)));
        return notifications.stream()
                .filter(notification -> onlineUserIds.contains(notification.receiverUserId()))
                .toList();
    }

    private Map<Long, Long> findUnreadCounts(List<CreatedNotification> notifications) {
        NotificationType type = notifications.getFirst().type();
        return stageTimer.time(type, NotificationStage.UNREAD_LOOKUP,
                        () -> notificationRepository.findAllUnreadCountsByReceiverUserIdIn(
                                receiverUserIds(notifications)))
                .stream()
                .collect(Collectors.toMap(
                        NotificationUnreadCount::getReceiverUserId,
                        NotificationUnreadCount::getUnreadCount
                ));
    }

    private List<Long> receiverUserIds(List<CreatedNotification> notifications) {
        return notifications.stream()
                .map(CreatedNotification::receiverUserId)
                .toList();
    }

    private void publish(CreatedNotification notification, long unreadCount) {
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
                CreatedNotification notification,
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
