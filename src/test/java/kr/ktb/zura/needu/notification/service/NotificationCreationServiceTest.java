package kr.ktb.zura.needu.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class NotificationCreationServiceTest {

    private final NotificationEventRepository notificationEventRepository = mock(NotificationEventRepository.class);
    private final NotificationBulkRepository notificationBulkRepository = mock(NotificationBulkRepository.class);
    private final NotificationSettingRepository notificationSettingRepository =
            mock(NotificationSettingRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final NotificationCreationService notificationCreationService = new NotificationCreationService(
            notificationEventRepository,
            notificationBulkRepository,
            notificationSettingRepository,
            eventPublisher
    );

    @Test
    void enabledAndDefaultUsers_createNotifications_savesOnlyEnabledUsers() {
        Set<Long> receiverUserIds = Set.of(1L, 2L, 3L);
        NotificationSetting enabled = NotificationSetting.create(
                1L, NotificationSettingType.FRIEND_BIRTHDAY, true);
        NotificationSetting disabled = NotificationSetting.create(
                2L, NotificationSettingType.FRIEND_BIRTHDAY, false);
        given(notificationSettingRepository.findAllByUserIdInAndType(
                receiverUserIds, NotificationSettingType.FRIEND_BIRTHDAY))
                .willReturn(List.of(enabled, disabled));
        given(notificationEventRepository.save(org.mockito.ArgumentMatchers.any(NotificationEvent.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(notificationBulkRepository.insertAll(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .willReturn(Map.of(1L, 101L, 3L, 103L));

        List<CreatedNotification> result = notificationCreationService.createNotifications(
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

        assertThat(result).extracting(CreatedNotification::receiverUserId).containsExactlyInAnyOrder(1L, 3L);
        assertThat(result).extracting(CreatedNotification::notificationId).containsExactlyInAnyOrder(101L, 103L);
        assertThat(result).extracting(CreatedNotification::type).containsOnly(NotificationType.FRIEND_BIRTHDAY);
        verify(notificationBulkRepository).insertAll(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        ArgumentCaptor<NotificationsCreatedEvent> eventCaptor =
                ArgumentCaptor.forClass(NotificationsCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().notifications())
                .extracting(NotificationsCreatedEvent.CreatedNotification::receiverUserId)
                .containsExactlyInAnyOrder(1L, 3L);
    }

    @Test
    void allUsersDisabled_createNotifications_doesNotSaveEventOrNotifications() {
        Set<Long> receiverUserIds = Set.of(1L);
        NotificationSetting disabled = NotificationSetting.create(1L, NotificationSettingType.CHAT, false);
        given(notificationSettingRepository.findAllByUserIdInAndType(
                receiverUserIds, NotificationSettingType.CHAT)).willReturn(List.of(disabled));

        List<CreatedNotification> result = notificationCreationService.createNotifications(
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

        assertThat(result).isEmpty();
        verify(notificationEventRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(notificationBulkRepository, never()).insertAll(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void duplicateDedupKey_skipsCreationAndReturnsEmptyList() {
        given(notificationEventRepository.existsByDedupKey("friend-birthday:10:2026-10-09")).willReturn(true);

        List<CreatedNotification> result = notificationCreationService.createNotifications(
                Set.of(1L),
                NotificationType.FRIEND_BIRTHDAY,
                null,
                "친구의 생일이에요.",
                "선물을 준비해 보세요.",
                NotificationResourceType.USER,
                10L,
                "friend-birthday:10:2026-10-09",
                LocalDateTime.of(2026, 11, 9, 0, 0)
        );

        assertThat(result).isEmpty();
        verify(notificationSettingRepository, never())
                .findAllByUserIdInAndType(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(notificationEventRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(notificationBulkRepository, never()).insertAll(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void nullDedupKey_createsWithoutDuplicateCheck() {
        Set<Long> receiverUserIds = Set.of(1L);
        given(notificationSettingRepository.findAllByUserIdInAndType(
                receiverUserIds, NotificationSettingType.POKE)).willReturn(List.of());
        given(notificationEventRepository.save(org.mockito.ArgumentMatchers.any(NotificationEvent.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(notificationBulkRepository.insertAll(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .willReturn(Map.of(1L, 101L));

        List<CreatedNotification> result = notificationCreationService.createNotifications(
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

        assertThat(result).extracting(CreatedNotification::receiverUserId).containsExactly(1L);
        verify(notificationEventRepository, never()).existsByDedupKey(org.mockito.ArgumentMatchers.any());
        verify(eventPublisher).publishEvent(org.mockito.ArgumentMatchers.any(NotificationsCreatedEvent.class));
    }

    @Test
    void receiversMissing_createNotifications_doesNotAccessRepositories() {
        List<CreatedNotification> result = notificationCreationService.createNotifications(
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

        assertThat(result).isEmpty();
        verify(notificationSettingRepository, never())
                .findAllByUserIdInAndType(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(notificationEventRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(notificationBulkRepository, never()).insertAll(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void requestsGiven_createNotificationsInBatch_savesEnabledUsersAndPublishesOnce() {
        given(notificationEventRepository.findAllDedupKeysIn(org.mockito.ArgumentMatchers.any()))
                .willReturn(List.of());
        given(notificationSettingRepository.findAllByUserIdInAndType(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(NotificationSettingType.FRIEND_BIRTHDAY)))
                .willReturn(List.of(NotificationSetting.create(2L, NotificationSettingType.FRIEND_BIRTHDAY, false)));
        givenSavedEventsHaveIds();
        given(notificationBulkRepository.insertAll(org.mockito.ArgumentMatchers.<List<NotificationInsertRow>>any(),
                org.mockito.ArgumentMatchers.any()))
                .willReturn(Map.of(1L, Map.of(1L, 101L), 2L, Map.of(3L, 203L)));

        List<CreatedNotification> result = notificationCreationService.createNotificationsInBatch(
                NotificationType.FRIEND_BIRTHDAY,
                List.of(birthdayRequest(10L, Set.of(1L, 2L)), birthdayRequest(20L, Set.of(2L, 3L)))
        );

        assertThat(result)
                .extracting(CreatedNotification::resourceId, CreatedNotification::receiverUserId,
                        CreatedNotification::notificationId)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(10L, 1L, 101L),
                        org.assertj.core.groups.Tuple.tuple(20L, 3L, 203L));
        ArgumentCaptor<List<NotificationInsertRow>> rowsCaptor = ArgumentCaptor.captor();
        verify(notificationBulkRepository).insertAll(rowsCaptor.capture(), org.mockito.ArgumentMatchers.any());
        assertThat(rowsCaptor.getValue())
                .extracting(NotificationInsertRow::notificationEventId, NotificationInsertRow::receiverUserId)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(1L, 1L),
                        org.assertj.core.groups.Tuple.tuple(2L, 3L));
        verify(notificationSettingRepository).findAllByUserIdInAndType(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(eventPublisher).publishEvent(org.mockito.ArgumentMatchers.any(NotificationsCreatedEvent.class));
    }

    @Test
    void existingAndRepeatedDedupKeys_createNotificationsInBatch_createsEachKeyOnce() {
        given(notificationEventRepository.findAllDedupKeysIn(org.mockito.ArgumentMatchers.any()))
                .willReturn(List.of("friend-birthday:10"));
        given(notificationSettingRepository.findAllByUserIdInAndType(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .willReturn(List.of());
        givenSavedEventsHaveIds();
        given(notificationBulkRepository.insertAll(org.mockito.ArgumentMatchers.<List<NotificationInsertRow>>any(),
                org.mockito.ArgumentMatchers.any()))
                .willReturn(Map.of(1L, Map.of(1L, 101L)));

        List<CreatedNotification> result = notificationCreationService.createNotificationsInBatch(
                NotificationType.FRIEND_BIRTHDAY,
                List.of(
                        birthdayRequest(10L, Set.of(1L)),
                        birthdayRequest(20L, Set.of(1L)),
                        birthdayRequest(20L, Set.of(2L))
                )
        );

        assertThat(result).extracting(CreatedNotification::resourceId).containsExactly(20L);
        verify(notificationEventRepository, org.mockito.Mockito.times(1))
                .save(org.mockito.ArgumentMatchers.any(NotificationEvent.class));
    }

    @Test
    void allUsersDisabled_createNotificationsInBatch_doesNotSaveEventOrNotifications() {
        given(notificationEventRepository.findAllDedupKeysIn(org.mockito.ArgumentMatchers.any()))
                .willReturn(List.of());
        given(notificationSettingRepository.findAllByUserIdInAndType(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .willReturn(List.of(NotificationSetting.create(1L, NotificationSettingType.FRIEND_BIRTHDAY, false)));

        List<CreatedNotification> result = notificationCreationService.createNotificationsInBatch(
                NotificationType.FRIEND_BIRTHDAY,
                List.of(birthdayRequest(10L, Set.of(1L)))
        );

        assertThat(result).isEmpty();
        verify(notificationEventRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(notificationBulkRepository, never()).insertAll(
                org.mockito.ArgumentMatchers.<List<NotificationInsertRow>>any(), org.mockito.ArgumentMatchers.any());
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void receiversMissing_createNotificationsInBatch_doesNotAccessRepositories() {
        List<CreatedNotification> result = notificationCreationService.createNotificationsInBatch(
                NotificationType.FRIEND_BIRTHDAY,
                List.of(birthdayRequest(10L, Set.of()))
        );

        assertThat(result).isEmpty();
        verify(notificationEventRepository, never()).findAllDedupKeysIn(org.mockito.ArgumentMatchers.any());
        verify(notificationSettingRepository, never())
                .findAllByUserIdInAndType(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    private void givenSavedEventsHaveIds() {
        AtomicLong nextId = new AtomicLong(1L);
        given(notificationEventRepository.save(org.mockito.ArgumentMatchers.any(NotificationEvent.class)))
                .willAnswer(invocation -> {
                    NotificationEvent event = invocation.getArgument(0);
                    ReflectionTestUtils.setField(event, "id", nextId.getAndIncrement());
                    return event;
                });
    }

    private NotificationCreateRequest birthdayRequest(Long friendUserId, Set<Long> receiverUserIds) {
        return new NotificationCreateRequest(
                receiverUserIds,
                null,
                "친구의 생일이에요.",
                "선물을 준비해 보세요.",
                NotificationResourceType.USER,
                friendUserId,
                "friend-birthday:" + friendUserId,
                LocalDateTime.of(2026, 11, 9, 0, 0)
        );
    }
}
