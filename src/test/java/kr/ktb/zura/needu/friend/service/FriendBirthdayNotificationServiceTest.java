package kr.ktb.zura.needu.friend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import kr.ktb.zura.needu.friend.dto.response.FriendBirthdayNotificationResult;
import kr.ktb.zura.needu.friend.dto.response.FriendBirthdayNotificationTarget;
import kr.ktb.zura.needu.friend.repository.FriendRepository;
import kr.ktb.zura.needu.notification.service.NotificationCreateRequest;
import kr.ktb.zura.needu.notification.service.NotificationCreationService;
import kr.ktb.zura.needu.notification.service.NotificationsCreatedEvent.CreatedNotification;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FriendBirthdayNotificationServiceTest {

    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 10, 19, 15, 0);

    private final FriendRepository friendRepository = org.mockito.Mockito.mock(FriendRepository.class);
    private final NotificationCreationService notificationCreationService =
            org.mockito.Mockito.mock(NotificationCreationService.class);
    private final FriendBirthdayNotificationService service = new FriendBirthdayNotificationService(
            friendRepository,
            notificationCreationService,
            7,
            "Asia/Seoul",
            2);

    @Test
    void multipleOwnersAndFriends_createNotifications_groupsReceiversByBirthdayFriend() {
        given(friendRepository.findAllBirthdayNotificationTargets(10, 19, false)).willReturn(List.of(
                new FriendBirthdayNotificationTarget(1L, 10L, "수연"),
                new FriendBirthdayNotificationTarget(2L, 10L, "수연"),
                new FriendBirthdayNotificationTarget(3L, 11L, "민지")
        ));

        service.createNotifications(LocalDate.of(2026, 10, 12));

        verify(notificationCreationService).createNotificationsInBatch(NotificationType.FRIEND_BIRTHDAY, List.of(
                birthdayRequest(Set.of(1L, 2L), 10L, "수연"),
                birthdayRequest(Set.of(3L), 11L, "민지")
        ));
    }

    @Test
    void friendsExceedChunkSize_createNotifications_createsByChunk() {
        given(friendRepository.findAllBirthdayNotificationTargets(10, 19, false)).willReturn(List.of(
                new FriendBirthdayNotificationTarget(1L, 10L, "수연"),
                new FriendBirthdayNotificationTarget(2L, 11L, "민지"),
                new FriendBirthdayNotificationTarget(3L, 12L, "지호")
        ));
        given(notificationCreationService.createNotificationsInBatch(eq(NotificationType.FRIEND_BIRTHDAY), anyList()))
                .willAnswer(invocation -> created(invocation.<List<?>>getArgument(1).size()));

        FriendBirthdayNotificationResult result = service.createNotifications(LocalDate.of(2026, 10, 12));

        ArgumentCaptor<List<NotificationCreateRequest>> chunkCaptor = ArgumentCaptor.captor();
        verify(notificationCreationService, times(2))
                .createNotificationsInBatch(eq(NotificationType.FRIEND_BIRTHDAY), chunkCaptor.capture());
        assertThat(chunkCaptor.getAllValues())
                .extracting(chunk -> chunk.stream().map(NotificationCreateRequest::resourceId).toList())
                .containsExactly(List.of(10L, 11L), List.of(12L));
        assertThat(result.createdNotificationCount()).isEqualTo(3);
        verify(notificationCreationService, never()).createNotifications(
                any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void chunkFails_createNotifications_retriesChunkOneByOneAndSkipsOnlyFailedFriend() {
        given(friendRepository.findAllBirthdayNotificationTargets(10, 19, false)).willReturn(List.of(
                new FriendBirthdayNotificationTarget(1L, 10L, "수연"),
                new FriendBirthdayNotificationTarget(2L, 11L, "민지")
        ));
        given(notificationCreationService.createNotificationsInBatch(eq(NotificationType.FRIEND_BIRTHDAY), anyList()))
                .willThrow(new IllegalStateException("batch failed"));
        given(notificationCreationService.createNotifications(
                any(), any(), any(), any(), any(), any(), eq(10L), any(), any()))
                .willThrow(new IllegalStateException("single failed"));
        given(notificationCreationService.createNotifications(
                any(), any(), any(), any(), any(), any(), eq(11L), any(), any()))
                .willReturn(created(1));

        FriendBirthdayNotificationResult result = service.createNotifications(LocalDate.of(2026, 10, 12));

        verify(notificationCreationService).createNotifications(
                Set.of(1L),
                NotificationType.FRIEND_BIRTHDAY,
                null,
                "수연 님의 생일이 7일 남았어요.",
                "지금 선물을 준비하면 여유 있게 도착해요.",
                NotificationResourceType.USER,
                10L,
                "friend-birthday:2026-10-19:10:d-7",
                EXPIRES_AT
        );
        verify(notificationCreationService).createNotifications(
                Set.of(2L),
                NotificationType.FRIEND_BIRTHDAY,
                null,
                "민지 님의 생일이 7일 남았어요.",
                "지금 선물을 준비하면 여유 있게 도착해요.",
                NotificationResourceType.USER,
                11L,
                "friend-birthday:2026-10-19:11:d-7",
                EXPIRES_AT
        );
        assertThat(result.createdNotificationCount()).isEqualTo(1);
    }

    @Test
    void marchFirstOfNonLeapYear_createNotifications_includesLeapDayBirthdays() {
        given(friendRepository.findAllBirthdayNotificationTargets(3, 1, true)).willReturn(List.of());

        service.createNotifications(LocalDate.of(2027, 2, 22));

        verify(friendRepository).findAllBirthdayNotificationTargets(3, 1, true);
        verifyNoInteractions(notificationCreationService);
    }

    @Test
    void februaryTwentyNinthOfLeapYear_createNotifications_doesNotIncludeMarchFirstRule() {
        given(friendRepository.findAllBirthdayNotificationTargets(2, 29, false)).willReturn(List.of());

        service.createNotifications(LocalDate.of(2028, 2, 22));

        verify(friendRepository).findAllBirthdayNotificationTargets(2, 29, false);
    }

    private NotificationCreateRequest birthdayRequest(Set<Long> receiverUserIds, Long friendUserId, String nickname) {
        return new NotificationCreateRequest(
                receiverUserIds,
                null,
                nickname + " 님의 생일이 7일 남았어요.",
                "지금 선물을 준비하면 여유 있게 도착해요.",
                NotificationResourceType.USER,
                friendUserId,
                "friend-birthday:2026-10-19:" + friendUserId + ":d-7",
                EXPIRES_AT
        );
    }

    private List<CreatedNotification> created(int count) {
        return Collections.nCopies(count, new CreatedNotification(
                1L, 1L, null, NotificationType.FRIEND_BIRTHDAY, "title", "body", null, null, EXPIRES_AT));
    }
}
