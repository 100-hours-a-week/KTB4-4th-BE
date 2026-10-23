package kr.ktb.zura.needu.notification.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import kr.ktb.zura.needu.notification.entity.NotificationEvent;
import kr.ktb.zura.needu.notification.entity.NotificationSetting;
import kr.ktb.zura.needu.notification.repository.NotificationBulkRepository;
import kr.ktb.zura.needu.notification.repository.NotificationBulkRepository.NotificationInsertRow;
import kr.ktb.zura.needu.notification.repository.NotificationEventRepository;
import kr.ktb.zura.needu.notification.repository.NotificationSettingRepository;
import kr.ktb.zura.needu.notification.service.NotificationsCreatedEvent.CreatedNotification;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationSettingType;
import kr.ktb.zura.needu.notification.type.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationCreationService {

    private static final int IN_CLAUSE_SIZE = 1000;

    private final NotificationEventRepository notificationEventRepository;
    private final NotificationBulkRepository notificationBulkRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * @param actorUserId 시스템이 발생시킨 알림이면 {@code null}
     * @param resourceType 연결할 대상 리소스가 없으면 {@code null}
     * @param resourceId 연결할 대상 리소스가 없으면 {@code null}
     * @param dedupKey 중복 생성을 막을 필요가 없는 이벤트면 {@code null}
     */
    @Transactional
    public List<CreatedNotification> createNotifications(
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
                        NotificationSetting::getUserId,
                        NotificationSetting::isEnabled
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
        LocalDateTime createdAt = LocalDateTime.now();
        Map<Long, Long> notificationIdByUserId = notificationBulkRepository.insertAll(
                event.getId(), enabledReceiverUserIds, createdAt, expiresAt);
        List<CreatedNotification> createdNotifications = enabledReceiverUserIds.stream()
                .map(userId -> CreatedNotification.of(event, notificationIdByUserId.get(userId), userId, createdAt))
                .toList();
        eventPublisher.publishEvent(new NotificationsCreatedEvent(createdNotifications));
        return createdNotifications;
    }

    /**
     * 같은 타입의 알림 이벤트 여러 개를 한 트랜잭션에서 만든다. 대량 생성 배치용으로,
     * dedup·설정 조회와 알림 INSERT를 요청마다 반복하지 않고 묶음 전체에 한 번씩 실행한다.
     * 하나라도 실패하면 묶음 전체가 롤백되므로 실패한 묶음은 호출하는 쪽에서 다시 처리한다.
     */
    @Transactional
    public List<CreatedNotification> createNotificationsInBatch(
            NotificationType type,
            List<NotificationCreateRequest> requests
    ) {
        List<NotificationCreateRequest> newRequests = excludeDuplicates(type, requests.stream()
                .filter(request -> !request.receiverUserIds().isEmpty())
                .toList());
        if (newRequests.isEmpty()) {
            return List.of();
        }

        NotificationSettingType settingType = type.getSettingType();
        List<Long> allReceiverUserIds = newRequests.stream()
                .flatMap(request -> request.receiverUserIds().stream())
                .distinct()
                .toList();
        Map<Long, Boolean> enabledByUserId = findEnabledByUserId(allReceiverUserIds, settingType);
        Set<Long> enabledUserIds = allReceiverUserIds.stream()
                .filter(userId -> enabledByUserId.getOrDefault(userId, settingType.isDefaultEnabled()))
                .collect(Collectors.toSet());

        Map<Long, NotificationEvent> eventsById = new HashMap<>();
        List<NotificationInsertRow> rows = new ArrayList<>();
        for (NotificationCreateRequest request : newRequests) {
            List<Long> enabledReceiverUserIds = request.receiverUserIds().stream()
                    .filter(enabledUserIds::contains)
                    .toList();
            if (enabledReceiverUserIds.isEmpty()) {
                continue;
            }
            NotificationEvent event = notificationEventRepository.save(NotificationEvent.create(
                    type,
                    request.actorUserId(),
                    request.title(),
                    request.body(),
                    request.resourceType(),
                    request.resourceId(),
                    request.dedupKey()
            ));
            eventsById.put(event.getId(), event);
            enabledReceiverUserIds.forEach(userId ->
                    rows.add(new NotificationInsertRow(event.getId(), userId, request.expiresAt())));
        }

        if (rows.isEmpty()) {
            return List.of();
        }

        LocalDateTime createdAt = LocalDateTime.now();
        Map<Long, Map<Long, Long>> notificationIdsByEventId = notificationBulkRepository.insertAll(rows, createdAt);
        List<CreatedNotification> createdNotifications = rows.stream()
                .map(row -> CreatedNotification.of(
                        eventsById.get(row.notificationEventId()),
                        notificationIdsByEventId.get(row.notificationEventId()).get(row.receiverUserId()),
                        row.receiverUserId(),
                        createdAt
                ))
                .toList();
        eventPublisher.publishEvent(new NotificationsCreatedEvent(createdNotifications));
        return createdNotifications;
    }

    // 이미 만든 이벤트와 묶음 안에서 겹친 dedupKey를 함께 거른다
    private List<NotificationCreateRequest> excludeDuplicates(
            NotificationType type,
            List<NotificationCreateRequest> requests
    ) {
        List<String> dedupKeys = requests.stream()
                .map(NotificationCreateRequest::dedupKey)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Set<String> seenDedupKeys = dedupKeys.isEmpty()
                ? new HashSet<>()
                : findExistingDedupKeys(dedupKeys);
        List<NotificationCreateRequest> newRequests = requests.stream()
                .filter(request -> request.dedupKey() == null || seenDedupKeys.add(request.dedupKey()))
                .toList();
        if (newRequests.size() < requests.size()) {
            log.info("Notification batch creation skipped duplicate dedupKeys. type={}, skippedCount={}",
                    type, requests.size() - newRequests.size());
        }
        return newRequests;
    }

    private Set<String> findExistingDedupKeys(List<String> dedupKeys) {
        Set<String> existingDedupKeys = new HashSet<>();
        for (List<String> chunk : partition(dedupKeys)) {
            existingDedupKeys.addAll(notificationEventRepository.findAllDedupKeysIn(chunk));
        }
        return existingDedupKeys;
    }

    private Map<Long, Boolean> findEnabledByUserId(List<Long> userIds, NotificationSettingType settingType) {
        Map<Long, Boolean> enabledByUserId = new HashMap<>();
        for (List<Long> chunk : partition(userIds)) {
            notificationSettingRepository.findAllByUserIdInAndType(chunk, settingType)
                    .forEach(setting -> enabledByUserId.put(setting.getUserId(), setting.isEnabled()));
        }
        return enabledByUserId;
    }

    private static <T> List<List<T>> partition(List<T> values) {
        List<List<T>> chunks = new ArrayList<>();
        for (int from = 0; from < values.size(); from += IN_CLAUSE_SIZE) {
            chunks.add(values.subList(from, Math.min(from + IN_CLAUSE_SIZE, values.size())));
        }
        return chunks;
    }
}
