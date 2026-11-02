package kr.ktb.zura.needu.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import kr.ktb.zura.needu.notification.entity.NotificationEvent;
import kr.ktb.zura.needu.notification.entity.NotificationSetting;
import kr.ktb.zura.needu.notification.repository.NotificationEventRepository;
import kr.ktb.zura.needu.notification.repository.NotificationRepository;
import kr.ktb.zura.needu.notification.repository.NotificationSettingRepository;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationSettingType;
import kr.ktb.zura.needu.notification.type.NotificationType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class NotificationCreationServiceTest {

    private static final int INSERT_CHUNK_SIZE = 2;
    private static final Long EVENT_ID = 100L;

    private final NotificationEventRepository notificationEventRepository = mock(NotificationEventRepository.class);
    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final NotificationSettingRepository notificationSettingRepository =
            mock(NotificationSettingRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final NotificationCreationService notificationCreationService = createService(INSERT_CHUNK_SIZE);

    @Test
    void enabledAndDefaultUsers_createNotifications_insertsOnlyEnabledUsers() {
        Set<Long> receiverUserIds = new LinkedHashSet<>(List.of(1L, 2L, 3L));
        NotificationSetting enabled = NotificationSetting.create(
                1L, NotificationSettingType.FRIEND_BIRTHDAY, true);
        NotificationSetting disabled = NotificationSetting.create(
                2L, NotificationSettingType.FRIEND_BIRTHDAY, false);
        given(notificationSettingRepository.findAllByUserIdInAndType(
                receiverUserIds, NotificationSettingType.FRIEND_BIRTHDAY))
                .willReturn(List.of(enabled, disabled));
        givenEventSaved();
        givenInsertReturnsSequentialIds(1_000L);
        LocalDateTime beforeCreation = LocalDateTime.now(ZoneOffset.UTC);

        int createdCount = createBirthdayNotifications(receiverUserIds);

        assertThat(createdCount).isEqualTo(2);
        verify(notificationRepository).insertAll(
                eq(EVENT_ID), eq(List.of(1L, 3L)), any(), eq(LocalDateTime.of(2026, 11, 9, 0, 0)));
        NotificationsCreatedEvent createdEvent = capturePublishedEvent();
        assertThat(createdEvent.notifications())
                .extracting(
                        NotificationsCreatedEvent.CreatedNotification::notificationId,
                        NotificationsCreatedEvent.CreatedNotification::receiverUserId,
                        NotificationsCreatedEvent.CreatedNotification::type,
                        NotificationsCreatedEvent.CreatedNotification::resourceId
                )
                .containsExactly(
                        tuple(1_000L, 1L, NotificationType.FRIEND_BIRTHDAY, 10L),
                        tuple(1_001L, 3L, NotificationType.FRIEND_BIRTHDAY, 10L)
                );
        assertThat(createdEvent.notifications())
                .extracting(NotificationsCreatedEvent.CreatedNotification::createdAt)
                .allSatisfy(createdAt -> assertThat(createdAt)
                        .isBetween(beforeCreation, LocalDateTime.now(ZoneOffset.UTC)));
        assertThat(stageTimerCount("prepare", NotificationType.FRIEND_BIRTHDAY)).isEqualTo(1);
        assertThat(stageTimerCount("insert", NotificationType.FRIEND_BIRTHDAY)).isEqualTo(1);
    }

    @Test
    void receiversExceedChunkSize_createNotifications_insertsByChunkKeepingReceiverOrder() {
        Set<Long> receiverUserIds = new LinkedHashSet<>(List.of(1L, 2L, 3L, 4L, 5L));
        given(notificationSettingRepository.findAllByUserIdInAndType(
                receiverUserIds, NotificationSettingType.FRIEND_BIRTHDAY)).willReturn(List.of());
        givenEventSaved();
        givenInsertReturnsSequentialIds(1_000L);

        int createdCount = createBirthdayNotifications(receiverUserIds);

        assertThat(createdCount).isEqualTo(5);
        ArgumentCaptor<List<Long>> chunkCaptor = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository, times(3)).insertAll(eq(EVENT_ID), chunkCaptor.capture(), any(), any());
        assertThat(chunkCaptor.getAllValues()).containsExactly(List.of(1L, 2L), List.of(3L, 4L), List.of(5L));
        assertThat(capturePublishedEvent().notifications())
                .extracting(
                        NotificationsCreatedEvent.CreatedNotification::notificationId,
                        NotificationsCreatedEvent.CreatedNotification::receiverUserId
                )
                .containsExactly(
                        tuple(1_000L, 1L), tuple(1_001L, 2L), tuple(1_002L, 3L), tuple(1_003L, 4L), tuple(1_004L, 5L));
    }

    @Test
    void allUsersDisabled_createNotifications_doesNotSaveEventOrNotifications() {
        Set<Long> receiverUserIds = Set.of(1L);
        NotificationSetting disabled = NotificationSetting.create(1L, NotificationSettingType.CHAT, false);
        given(notificationSettingRepository.findAllByUserIdInAndType(
                receiverUserIds, NotificationSettingType.CHAT)).willReturn(List.of(disabled));

        int createdCount = notificationCreationService.createNotifications(
                receiverUserIds,
                NotificationType.CHAT,
                2L,
                "새로운 답변이 도착했어요.",
                "친구가 상품에 대한 의견을 남겼어요.",
                NotificationResourceType.AI_CONVERSATION,
                10L,
                "chat:10:20",
                LocalDateTime.of(2026, 11, 9, 0, 0)
        );

        assertThat(createdCount).isZero();
        verify(notificationEventRepository, never()).save(any());
        verify(notificationRepository, never()).insertAll(any(), anyList(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
        assertThat(stageTimerCount("prepare", NotificationType.CHAT)).isEqualTo(1);
        assertThat(meterRegistry.find("needu.notification.creation").tag("stage", "insert").timer()).isNull();
    }

    @Test
    void duplicateDedupKey_skipsCreationAndReturnsZero() {
        given(notificationEventRepository.existsByDedupKey("friend-birthday:10:2026-10-09")).willReturn(true);

        int createdCount = createBirthdayNotifications(Set.of(1L));

        assertThat(createdCount).isZero();
        verify(notificationSettingRepository, never()).findAllByUserIdInAndType(any(), any());
        verify(notificationEventRepository, never()).save(any());
        verify(notificationRepository, never()).insertAll(any(), anyList(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void nullDedupKey_createsWithoutDuplicateCheck() {
        Set<Long> receiverUserIds = Set.of(1L);
        given(notificationSettingRepository.findAllByUserIdInAndType(
                receiverUserIds, NotificationSettingType.POKE)).willReturn(List.of());
        givenEventSaved();
        givenInsertReturnsSequentialIds(1_000L);

        int createdCount = notificationCreationService.createNotifications(
                receiverUserIds,
                NotificationType.POKE,
                2L,
                "친구가 콕 찔렀어요.",
                "AI 대화를 시작해 보세요.",
                NotificationResourceType.USER,
                2L,
                null,
                LocalDateTime.of(2026, 11, 9, 0, 0)
        );

        assertThat(createdCount).isEqualTo(1);
        verify(notificationEventRepository, never()).existsByDedupKey(any());
        verify(eventPublisher).publishEvent(any(NotificationsCreatedEvent.class));
    }

    @Test
    void receiversMissing_createNotifications_doesNotAccessRepositories() {
        int createdCount = notificationCreationService.createNotifications(
                Set.of(),
                NotificationType.POKE,
                2L,
                "친구가 콕 찔렀어요.",
                "AI 대화를 시작해 보세요.",
                NotificationResourceType.USER,
                2L,
                null,
                LocalDateTime.of(2026, 11, 9, 0, 0)
        );

        assertThat(createdCount).isZero();
        verify(notificationSettingRepository, never()).findAllByUserIdInAndType(any(), any());
        verify(notificationEventRepository, never()).save(any());
        verify(notificationRepository, never()).insertAll(any(), anyList(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void nonPositiveInsertChunkSize_create_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> createService(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("insert-chunk-size");
    }

    private NotificationCreationService createService(int insertChunkSize) {
        return new NotificationCreationService(
                notificationEventRepository,
                notificationRepository,
                notificationSettingRepository,
                eventPublisher,
                meterRegistry,
                insertChunkSize
        );
    }

    private int createBirthdayNotifications(Set<Long> receiverUserIds) {
        return notificationCreationService.createNotifications(
                receiverUserIds,
                NotificationType.FRIEND_BIRTHDAY,
                null,
                "친구의 생일이에요.",
                "선물을 준비해 보세요.",
                NotificationResourceType.USER,
                10L,
                "friend-birthday:10:2026-10-09",
                LocalDateTime.of(2026, 11, 9, 0, 0)
        );
    }

    private void givenEventSaved() {
        given(notificationEventRepository.save(any(NotificationEvent.class))).willAnswer(invocation -> {
            NotificationEvent event = invocation.getArgument(0);
            ReflectionTestUtils.setField(event, "id", EVENT_ID);
            return event;
        });
    }

    // insertAll 호출마다 받은 수신자 수만큼 이어지는 ID를 돌려준다 (실제 IDENTITY 다중 행 INSERT와 같은 모양)
    private void givenInsertReturnsSequentialIds(long firstId) {
        AtomicLong nextId = new AtomicLong(firstId);
        given(notificationRepository.insertAll(any(), anyList(), any(), any())).willAnswer(invocation -> {
            List<Long> receivers = invocation.getArgument(1);
            return receivers.stream().map(receiver -> nextId.getAndIncrement()).toList();
        });
    }

    private NotificationsCreatedEvent capturePublishedEvent() {
        ArgumentCaptor<NotificationsCreatedEvent> eventCaptor = ArgumentCaptor.forClass(NotificationsCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        return eventCaptor.getValue();
    }

    private long stageTimerCount(String stage, NotificationType type) {
        return meterRegistry.get("needu.notification.creation")
                .tag("stage", stage)
                .tag("type", type.name())
                .timer()
                .count();
    }
}
