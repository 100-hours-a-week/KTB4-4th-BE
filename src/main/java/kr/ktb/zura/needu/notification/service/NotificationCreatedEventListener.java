package kr.ktb.zura.needu.notification.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import kr.ktb.zura.needu.notification.repository.NotificationRepository;
import kr.ktb.zura.needu.notification.repository.NotificationUnreadCount;
import kr.ktb.zura.needu.notification.sse.NotificationRedisEvent;
import kr.ktb.zura.needu.notification.type.NotificationCategory;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
public class NotificationCreatedEventListener {

    private static final String STAGE_TIMER = "needu.notification.creation";

    private final NotificationRepository notificationRepository;
    private final NotificationEventPublisher notificationEventPublisher;
    private final NotificationPreviewService notificationPreviewService;
    private final JsonMapper jsonMapper;
    private final MeterRegistry meterRegistry;
    private final int publishChunkSize;

    public NotificationCreatedEventListener(
            NotificationRepository notificationRepository,
            NotificationEventPublisher notificationEventPublisher,
            NotificationPreviewService notificationPreviewService,
            JsonMapper jsonMapper,
            MeterRegistry meterRegistry,
            @Value("${needu.notification.publish-chunk-size}") int publishChunkSize
    ) {
        this.notificationRepository = notificationRepository;
        this.notificationEventPublisher = notificationEventPublisher;
        this.notificationPreviewService = notificationPreviewService;
        this.jsonMapper = jsonMapper;
        this.meterRegistry = meterRegistry;
        this.publishChunkSize = publishChunkSize;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(NotificationsCreatedEvent event) {
        if (event.notifications().isEmpty()) {
            return;
        }

        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            publishAll(event);
        } finally {
            sample.stop(Timer.builder(STAGE_TIMER)
                    .tag("stage", "publish")
                    .tag("type", event.notifications().getFirst().type().name())
                    .register(meterRegistry));
        }
    }

    private void publishAll(NotificationsCreatedEvent event) {
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

        // 공통 값이 같은 알림끼리 묶어 받는 사용자 수만큼이 아니라 chunk 수만큼만 발행한다
        Map<NotificationCommonData, List<NotificationsCreatedEvent.CreatedNotification>> notificationsByCommonData =
                event.notifications().stream()
                        .collect(Collectors.groupingBy(
                                NotificationCommonData::from,
                                LinkedHashMap::new,
                                Collectors.toList()
                        ));
        notificationsByCommonData.forEach((commonData, notifications) -> {
            for (int fromIndex = 0; fromIndex < notifications.size(); fromIndex += publishChunkSize) {
                int toIndex = Math.min(fromIndex + publishChunkSize, notifications.size());
                publish(commonData, notifications.subList(fromIndex, toIndex), unreadCountByUserId);
            }
        });
    }

    private void publish(
            NotificationCommonData commonData,
            List<NotificationsCreatedEvent.CreatedNotification> notifications,
            Map<Long, Long> unreadCountByUserId
    ) {
        try {
            List<NotificationRedisEvent.Recipient> recipients = notifications.stream()
                    .map(notification -> toRecipient(
                            notification,
                            unreadCountByUserId.getOrDefault(notification.receiverUserId(), 0L)
                    ))
                    .toList();
            notificationEventPublisher.publish(new NotificationRedisEvent(
                    jsonMapper.valueToTree(commonData),
                    recipients
            ));
        } catch (RuntimeException exception) {
            log.warn(
                    "Notification Redis publish failed. type={}, firstNotificationId={}, recipientCount={}",
                    commonData.type(),
                    notifications.getFirst().notificationId(),
                    notifications.size(),
                    exception
            );
        }
    }

    private NotificationRedisEvent.Recipient toRecipient(
            NotificationsCreatedEvent.CreatedNotification notification,
            long unreadCount
    ) {
        NotificationPreview preview = notificationPreviewService.findPreview(
                notification.receiverUserId(), notification.resourceType(), notification.resourceId());
        return new NotificationRedisEvent.Recipient(
                notification.receiverUserId(),
                jsonMapper.valueToTree(NotificationRecipientData.from(notification, preview)),
                unreadCount
        );
    }

    private record NotificationCommonData(
            NotificationCategory category,
            NotificationType type,
            String title,
            String body,
            NotificationResourceType resourceType,
            Long resourceId,
            boolean targetAvailable
    ) {

        private static NotificationCommonData from(NotificationsCreatedEvent.CreatedNotification notification) {
            return new NotificationCommonData(
                    notification.category(),
                    notification.type(),
                    notification.title(),
                    notification.body(),
                    notification.resourceType(),
                    notification.resourceId(),
                    notification.type() == NotificationType.POKE
                            || (notification.resourceType() != null && notification.resourceId() != null)
            );
        }
    }

    private record NotificationRecipientData(
            Long notificationId,
            NotificationPreview preview,
            LocalDateTime createdAt
    ) {

        private static NotificationRecipientData from(
                NotificationsCreatedEvent.CreatedNotification notification,
                NotificationPreview preview
        ) {
            return new NotificationRecipientData(notification.notificationId(), preview, notification.createdAt());
        }
    }
}
