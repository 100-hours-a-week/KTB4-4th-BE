package kr.ktb.zura.needu.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import kr.ktb.zura.needu.notification.entity.Notification;
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

class NotificationCreationServiceTest {

    private final NotificationEventRepository notificationEventRepository = mock(NotificationEventRepository.class);
    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final NotificationSettingRepository notificationSettingRepository =
            mock(NotificationSettingRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final NotificationCreationService notificationCreationService = new NotificationCreationService(
            notificationEventRepository,
            notificationRepository,
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
        given(notificationRepository.saveAll(org.mockito.ArgumentMatchers.<List<Notification>>any()))
                .willAnswer(invocation -> invocation.getArgument(0));

        List<Notification> result = notificationCreationService.createNotifications(
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

        assertThat(result).extracting(Notification::getReceiverUserId).containsExactlyInAnyOrder(1L, 3L);
        assertThat(result)
                .extracting(Notification::getEvent)
                .containsOnly(result.getFirst().getEvent());
        verify(notificationRepository).saveAll(org.mockito.ArgumentMatchers.any());
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

        List<Notification> result = notificationCreationService.createNotifications(
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
        verify(notificationRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void duplicateDedupKey_skipsCreationAndReturnsEmptyList() {
        given(notificationEventRepository.existsByDedupKey("friend-birthday:10:2026-10-09")).willReturn(true);

        List<Notification> result = notificationCreationService.createNotifications(
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
        verify(notificationRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void nullDedupKey_createsWithoutDuplicateCheck() {
        Set<Long> receiverUserIds = Set.of(1L);
        given(notificationSettingRepository.findAllByUserIdInAndType(
                receiverUserIds, NotificationSettingType.POKE)).willReturn(List.of());
        given(notificationEventRepository.save(org.mockito.ArgumentMatchers.any(NotificationEvent.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(notificationRepository.saveAll(org.mockito.ArgumentMatchers.<List<Notification>>any()))
                .willAnswer(invocation -> invocation.getArgument(0));

        List<Notification> result = notificationCreationService.createNotifications(
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

        assertThat(result).extracting(Notification::getReceiverUserId).containsExactly(1L);
        verify(notificationEventRepository, never()).existsByDedupKey(org.mockito.ArgumentMatchers.any());
        verify(eventPublisher).publishEvent(org.mockito.ArgumentMatchers.any(NotificationsCreatedEvent.class));
    }

    @Test
    void receiversMissing_createNotifications_doesNotAccessRepositories() {
        List<Notification> result = notificationCreationService.createNotifications(
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
        verify(notificationRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }
}
