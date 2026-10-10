package kr.ktb.zura.needu.notification.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import kr.ktb.zura.needu.notification.entity.Notification;
import kr.ktb.zura.needu.notification.entity.NotificationEvent;
import kr.ktb.zura.needu.notification.repository.NotificationEventRepository;
import kr.ktb.zura.needu.notification.repository.NotificationRepository;
import kr.ktb.zura.needu.notification.repository.NotificationSettingRepository;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationSettingType;
import kr.ktb.zura.needu.notification.type.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationCreationService {

    private final NotificationEventRepository notificationEventRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * @param actorUserId 시스템이 발생시킨 알림이면 {@code null}
     * @param resourceType 연결할 대상 리소스가 없으면 {@code null}
     * @param resourceId 연결할 대상 리소스가 없으면 {@code null}
     * @param dedupKey 중복 생성을 막을 필요가 없는 이벤트면 {@code null}
     */
    @Transactional
    public List<Notification> createNotifications(
            Set<Long> receiverUserIds,
            NotificationType type,
            Long actorUserId,
            String title,
            String body,
            NotificationResourceType resourceType,
            Long resourceId,
            String dedupKey,
            LocalDateTime expiresAt
    ) {
        if (receiverUserIds.isEmpty()) {
            return List.of();
        }

        NotificationSettingType settingType = type.getSettingType();
        Map<Long, Boolean> enabledByUserId = notificationSettingRepository
                .findAllByUserIdInAndType(receiverUserIds, settingType)
                .stream()
                .collect(Collectors.toMap(
                        setting -> setting.getUserId(),
                        setting -> setting.isEnabled()
                ));
        List<Long> enabledReceiverUserIds = receiverUserIds.stream()
                .filter(userId -> enabledByUserId.getOrDefault(userId, settingType.isDefaultEnabled()))
                .toList();

        if (enabledReceiverUserIds.isEmpty()) {
            return List.of();
        }

        NotificationEvent event = notificationEventRepository.save(NotificationEvent.create(
                type,
                actorUserId,
                title,
                body,
                resourceType,
                resourceId,
                dedupKey
        ));
        List<Notification> notifications = enabledReceiverUserIds.stream()
                .map(userId -> Notification.create(event, userId, expiresAt))
                .toList();

        List<Notification> savedNotifications = notificationRepository.saveAll(notifications);
        eventPublisher.publishEvent(NotificationsCreatedEvent.from(savedNotifications));
        return savedNotifications;
    }
}
