package kr.ktb.zura.needu.notification.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import kr.ktb.zura.needu.notification.entity.NotificationEvent;
import kr.ktb.zura.needu.notification.repository.NotificationEventRepository;
import kr.ktb.zura.needu.notification.repository.NotificationRepository;
import kr.ktb.zura.needu.notification.repository.NotificationSettingRepository;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationSettingType;
import kr.ktb.zura.needu.notification.type.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class NotificationCreationService {

    private final NotificationEventRepository notificationEventRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final int insertChunkSize;

    public NotificationCreationService(
            NotificationEventRepository notificationEventRepository,
            NotificationRepository notificationRepository,
            NotificationSettingRepository notificationSettingRepository,
            ApplicationEventPublisher eventPublisher,
            @Value("${needu.notification.insert-chunk-size}") int insertChunkSize
    ) {
        // 0 이하면 chunk 반복이 끝나지 않으므로 기동 시점에 막는다
        if (insertChunkSize <= 0) {
            throw new IllegalArgumentException(
                    "needu.notification.insert-chunk-size must be positive. value=" + insertChunkSize);
        }
        this.notificationEventRepository = notificationEventRepository;
        this.notificationRepository = notificationRepository;
        this.notificationSettingRepository = notificationSettingRepository;
        this.eventPublisher = eventPublisher;
        this.insertChunkSize = insertChunkSize;
    }

    /**
     * @param actorUserId 시스템이 발생시킨 알림이면 {@code null}
     * @param resourceType 연결할 대상 리소스가 없으면 {@code null}
     * @param resourceId 연결할 대상 리소스가 없으면 {@code null}
     * @param dedupKey 중복 생성을 막을 필요가 없는 이벤트면 {@code null}
     * @return 생성한 알림 수
     */
    @Transactional
    public int createNotifications(
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
            return 0;
        }

        List<Long> enabledReceiverUserIds = findEnabledReceiverUserIds(receiverUserIds, type, dedupKey);
        if (enabledReceiverUserIds.isEmpty()) {
            return 0;
        }

        LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);
        NotificationEvent event = notificationEventRepository.save(NotificationEvent.create(
                type,
                actorUserId,
                title,
                body,
                resourceType,
                resourceId,
                dedupKey
        ));
        List<Long> notificationIds = insertNotifications(event, enabledReceiverUserIds, createdAt, expiresAt);
        NotificationsCreatedEvent createdEvent =
                NotificationsCreatedEvent.of(event, notificationIds, enabledReceiverUserIds, createdAt);
        eventPublisher.publishEvent(createdEvent);
        return createdEvent.notifications().size();
    }

    // IDENTITY 키라 JPA saveAll은 행마다 INSERT하므로 JDBC 배치로 chunk마다 한 문장씩 저장한다
    private List<Long> insertNotifications(
            NotificationEvent event,
            List<Long> receiverUserIds,
            LocalDateTime createdAt,
            LocalDateTime expiresAt
    ) {
        List<Long> notificationIds = new ArrayList<>(receiverUserIds.size());
        for (int fromIndex = 0; fromIndex < receiverUserIds.size(); fromIndex += insertChunkSize) {
            int toIndex = Math.min(fromIndex + insertChunkSize, receiverUserIds.size());
            notificationIds.addAll(notificationRepository.insertAll(
                    event.getId(), receiverUserIds.subList(fromIndex, toIndex), createdAt, expiresAt));
        }
        return notificationIds;
    }

    private List<Long> findEnabledReceiverUserIds(Set<Long> receiverUserIds, NotificationType type, String dedupKey) {
        // 동시 요청이 함께 통과하면 UNIQUE 제약 위반은 그대로 전파된다.
        // 제약 위반 시점에 트랜잭션이 rollback-only가 되어 catch로는 조용히 넘길 수 없다.
        if (dedupKey != null && notificationEventRepository.existsByDedupKey(dedupKey)) {
            log.info("Notification creation skipped by duplicate dedupKey. type={}, dedupKey={}", type, dedupKey);
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
        return receiverUserIds.stream()
                .filter(userId -> enabledByUserId.getOrDefault(userId, settingType.isDefaultEnabled()))
                .toList();
    }
}
