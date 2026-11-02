package kr.ktb.zura.needu.notification.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import kr.ktb.zura.needu.notification.entity.NotificationEvent;
import kr.ktb.zura.needu.notification.repository.NotificationEventRepository;
import kr.ktb.zura.needu.notification.repository.NotificationInsertRow;
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

    private static final String STAGE_TIMER = "needu.notification.creation";

    private final NotificationEventRepository notificationEventRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;
    private final int insertChunkSize;

    public NotificationCreationService(
            NotificationEventRepository notificationEventRepository,
            NotificationRepository notificationRepository,
            NotificationSettingRepository notificationSettingRepository,
            ApplicationEventPublisher eventPublisher,
            MeterRegistry meterRegistry,
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
        this.meterRegistry = meterRegistry;
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
        return createNotifications(List.of(new NotificationCreationCommand(
                receiverUserIds, type, actorUserId, title, body, resourceType, resourceId, dedupKey, expiresAt)));
    }

    /**
     * 여러 알림 이벤트를 한 트랜잭션에서 만든다.
     * dedup·알림 설정 조회, 알림 INSERT, 커밋 후 발행을 이벤트마다가 아니라 한 번에 처리하고, 하나라도 실패하면 함께 롤백된다.
     *
     * @return 생성한 알림 수
     */
    @Transactional
    public int createNotifications(List<NotificationCreationCommand> commands) {
        List<NotificationCreationCommand> requestedCommands = commands.stream()
                .filter(command -> !command.receiverUserIds().isEmpty())
                .toList();
        if (requestedCommands.isEmpty()) {
            return 0;
        }

        // 구간 타이머 태그는 첫 명령의 타입으로 붙인다 (생일 배치처럼 묶어 부르는 쪽은 모두 같은 타입이다)
        NotificationType timerType = requestedCommands.getFirst().type();
        List<EnabledNotification> enabledNotifications = stageTimer("prepare", timerType)
                .record(() -> findEnabledNotifications(requestedCommands));
        if (enabledNotifications.isEmpty()) {
            return 0;
        }

        LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);
        NotificationsCreatedEvent createdEvent = stageTimer("insert", timerType)
                .record(() -> saveNotifications(enabledNotifications, createdAt));
        eventPublisher.publishEvent(createdEvent);
        return createdEvent.notifications().size();
    }

    private NotificationsCreatedEvent saveNotifications(
            List<EnabledNotification> enabledNotifications,
            LocalDateTime createdAt
    ) {
        List<NotificationEvent> events = enabledNotifications.stream()
                .map(notification -> notificationEventRepository.save(notification.toEvent()))
                .toList();
        List<NotificationInsertRow> rows = new ArrayList<>();
        for (int index = 0; index < enabledNotifications.size(); index++) {
            EnabledNotification notification = enabledNotifications.get(index);
            Long eventId = events.get(index).getId();
            notification.receiverUserIds().forEach(receiverUserId -> rows.add(new NotificationInsertRow(
                    eventId, receiverUserId, notification.command().expiresAt())));
        }
        List<Long> notificationIds = insertNotifications(rows, createdAt);

        List<NotificationsCreatedEvent.CreatedNotification> createdNotifications = new ArrayList<>(rows.size());
        int fromIndex = 0;
        for (int index = 0; index < enabledNotifications.size(); index++) {
            List<Long> receiverUserIds = enabledNotifications.get(index).receiverUserIds();
            int toIndex = fromIndex + receiverUserIds.size();
            createdNotifications.addAll(NotificationsCreatedEvent.of(
                    events.get(index), notificationIds.subList(fromIndex, toIndex), receiverUserIds, createdAt
            ).notifications());
            fromIndex = toIndex;
        }
        return new NotificationsCreatedEvent(createdNotifications);
    }

    // IDENTITY 키라 JPA saveAll은 행마다 INSERT하므로 JDBC 배치로 chunk마다 한 문장씩 저장한다
    private List<Long> insertNotifications(List<NotificationInsertRow> rows, LocalDateTime createdAt) {
        List<Long> notificationIds = new ArrayList<>(rows.size());
        for (int fromIndex = 0; fromIndex < rows.size(); fromIndex += insertChunkSize) {
            int toIndex = Math.min(fromIndex + insertChunkSize, rows.size());
            notificationIds.addAll(notificationRepository.insertAll(rows.subList(fromIndex, toIndex), createdAt));
        }
        return notificationIds;
    }

    private List<EnabledNotification> findEnabledNotifications(List<NotificationCreationCommand> commands) {
        List<NotificationCreationCommand> newCommands = excludeDuplicateCommands(commands);
        Map<NotificationSettingType, Map<Long, Boolean>> enabledByUserIdBySettingType =
                findEnabledByUserIdBySettingType(newCommands);
        return newCommands.stream()
                .map(command -> {
                    NotificationSettingType settingType = command.type().getSettingType();
                    Map<Long, Boolean> enabledByUserId = enabledByUserIdBySettingType.get(settingType);
                    List<Long> enabledReceiverUserIds = command.receiverUserIds().stream()
                            .filter(userId -> enabledByUserId.getOrDefault(userId, settingType.isDefaultEnabled()))
                            .toList();
                    return new EnabledNotification(command, enabledReceiverUserIds);
                })
                .filter(notification -> !notification.receiverUserIds().isEmpty())
                .toList();
    }

    private List<NotificationCreationCommand> excludeDuplicateCommands(List<NotificationCreationCommand> commands) {
        // 동시 요청이 함께 통과하면 UNIQUE 제약 위반은 그대로 전파된다.
        // 제약 위반 시점에 트랜잭션이 rollback-only가 되어 catch로는 조용히 넘길 수 없다.
        List<String> dedupKeys = commands.stream()
                .map(NotificationCreationCommand::dedupKey)
                .filter(Objects::nonNull)
                .toList();
        Set<String> usedDedupKeys = dedupKeys.isEmpty()
                ? new HashSet<>()
                : new HashSet<>(notificationEventRepository.findAllDedupKeysByDedupKeyIn(dedupKeys));
        List<NotificationCreationCommand> newCommands = new ArrayList<>(commands.size());
        for (NotificationCreationCommand command : commands) {
            if (command.dedupKey() != null && !usedDedupKeys.add(command.dedupKey())) {
                log.info("Notification creation skipped by duplicate dedupKey. type={}, dedupKey={}",
                        command.type(), command.dedupKey());
                continue;
            }
            newCommands.add(command);
        }
        return newCommands;
    }

    private Map<NotificationSettingType, Map<Long, Boolean>> findEnabledByUserIdBySettingType(
            List<NotificationCreationCommand> commands
    ) {
        Map<NotificationSettingType, Set<Long>> receiverUserIdsBySettingType = commands.stream()
                .collect(Collectors.groupingBy(
                        command -> command.type().getSettingType(),
                        LinkedHashMap::new,
                        Collectors.flatMapping(
                                command -> command.receiverUserIds().stream(),
                                Collectors.toCollection(LinkedHashSet::new)
                        )
                ));
        Map<NotificationSettingType, Map<Long, Boolean>> enabledByUserIdBySettingType = new HashMap<>();
        receiverUserIdsBySettingType.forEach((settingType, receiverUserIds) ->
                enabledByUserIdBySettingType.put(settingType, notificationSettingRepository
                        .findAllByUserIdInAndType(receiverUserIds, settingType)
                        .stream()
                        .collect(Collectors.toMap(
                                setting -> setting.getUserId(),
                                setting -> setting.isEnabled()
                        ))));
        return enabledByUserIdBySettingType;
    }

    private Timer stageTimer(String stage, NotificationType type) {
        return Timer.builder(STAGE_TIMER)
                .tag("stage", stage)
                .tag("type", type.name())
                .register(meterRegistry);
    }

    private record EnabledNotification(NotificationCreationCommand command, List<Long> receiverUserIds) {

        private NotificationEvent toEvent() {
            return NotificationEvent.create(
                    command.type(),
                    command.actorUserId(),
                    command.title(),
                    command.body(),
                    command.resourceType(),
                    command.resourceId(),
                    command.dedupKey()
            );
        }
    }
}
