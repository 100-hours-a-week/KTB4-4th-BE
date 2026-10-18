package kr.ktb.zura.needu.friend.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import kr.ktb.zura.needu.friend.dto.response.FriendJoinedNotificationTarget;
import kr.ktb.zura.needu.friend.repository.FriendRepository;
import kr.ktb.zura.needu.notification.service.NotificationCreationService;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FriendJoinedNotificationServiceTest {

    private FriendRepository friendRepository;
    private NotificationCreationService notificationCreationService;
    private FriendJoinedNotificationService friendJoinedNotificationService;

    @BeforeEach
    void setUp() {
        friendRepository = mock(FriendRepository.class);
        notificationCreationService = mock(NotificationCreationService.class);
        friendJoinedNotificationService = new FriendJoinedNotificationService(
                friendRepository, notificationCreationService);
    }

    @Test
    void mutualFriends_createNotifications_createsFriendJoinedNotification() {
        when(friendRepository.findAllFriendJoinedNotificationTargets(1L, List.of(2L, 3L)))
                .thenReturn(List.of(new FriendJoinedNotificationTarget(2L, "새친구")));
        LocalDateTime beforeCreation = LocalDateTime.now(ZoneOffset.UTC);

        friendJoinedNotificationService.createNotifications(1L, List.of(2L, 3L));

        ArgumentCaptor<LocalDateTime> expiresAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(notificationCreationService).createNotifications(
                eq(Set.of(1L)),
                eq(NotificationType.FRIEND_JOINED),
                eq(2L),
                eq("새친구님이 NEEDU에 가입했어요!"),
                eq("새로운 친구의 취향을 확인해 보세요."),
                eq(NotificationResourceType.USER),
                eq(2L),
                eq("friend-joined:1:2"),
                expiresAtCaptor.capture()
        );
        assertThat(expiresAtCaptor.getValue()).isBetween(
                beforeCreation.plusDays(30), LocalDateTime.now(ZoneOffset.UTC).plusDays(30));
    }

    @Test
    void noNewFriends_createNotifications_skipsTargetLookup() {
        friendJoinedNotificationService.createNotifications(1L, List.of());

        verify(friendRepository, never()).findAllFriendJoinedNotificationTargets(anyLong(), anyCollection());
        verify(notificationCreationService, never()).createNotifications(
                any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void oneNotificationFails_createNotifications_continuesNextTargetWithoutThrowing() {
        when(friendRepository.findAllFriendJoinedNotificationTargets(1L, List.of(2L, 3L)))
                .thenReturn(List.of(
                        new FriendJoinedNotificationTarget(2L, "실패친구"),
                        new FriendJoinedNotificationTarget(3L, "성공친구")
                ));
        when(notificationCreationService.createNotifications(
                any(), any(), eq(2L), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException());

        assertThatCode(() -> friendJoinedNotificationService.createNotifications(1L, List.of(2L, 3L)))
                .doesNotThrowAnyException();

        verify(notificationCreationService).createNotifications(
                any(), any(), eq(3L), any(), any(), any(), any(), eq("friend-joined:1:3"), any());
    }

    @Test
    void targetLookupFails_createNotifications_doesNotThrow() {
        when(friendRepository.findAllFriendJoinedNotificationTargets(1L, List.of(2L)))
                .thenThrow(new IllegalStateException());

        assertThatCode(() -> friendJoinedNotificationService.createNotifications(1L, List.of(2L)))
                .doesNotThrowAnyException();

        verify(notificationCreationService, never()).createNotifications(
                any(), any(), any(), any(), any(), any(), any(), any(), any());
    }
}
