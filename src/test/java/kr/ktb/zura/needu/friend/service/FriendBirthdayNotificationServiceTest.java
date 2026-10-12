package kr.ktb.zura.needu.friend.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import kr.ktb.zura.needu.friend.dto.response.FriendBirthdayNotificationTarget;
import kr.ktb.zura.needu.friend.repository.FriendRepository;
import kr.ktb.zura.needu.notification.service.NotificationCreationService;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationType;
import org.junit.jupiter.api.Test;

class FriendBirthdayNotificationServiceTest {

    private final FriendRepository friendRepository = org.mockito.Mockito.mock(FriendRepository.class);
    private final NotificationCreationService notificationCreationService =
            org.mockito.Mockito.mock(NotificationCreationService.class);
    private final FriendBirthdayNotificationService service = new FriendBirthdayNotificationService(
            friendRepository, notificationCreationService, 8, "Asia/Seoul");

    @Test
    void multipleOwnersAndFriends_createNotifications_groupsReceiversByBirthdayFriend() {
        given(friendRepository.findAllBirthdayNotificationTargets(10, 20, false)).willReturn(List.of(
                new FriendBirthdayNotificationTarget(1L, 10L, "수연"),
                new FriendBirthdayNotificationTarget(2L, 10L, "수연"),
                new FriendBirthdayNotificationTarget(3L, 11L, "민지")
        ));

        service.createNotifications(LocalDate.of(2026, 10, 12));

        verify(notificationCreationService).createNotifications(
                Set.of(1L, 2L),
                NotificationType.FRIEND_BIRTHDAY,
                null,
                "수연 님의 생일이 8일 남았어요.",
                "지금 선물을 준비하면 여유 있게 도착해요.",
                NotificationResourceType.USER,
                10L,
                "friend-birthday:2026-10-20:10:d-8",
                LocalDateTime.of(2026, 10, 20, 15, 0)
        );
        verify(notificationCreationService).createNotifications(
                Set.of(3L),
                NotificationType.FRIEND_BIRTHDAY,
                null,
                "민지 님의 생일이 8일 남았어요.",
                "지금 선물을 준비하면 여유 있게 도착해요.",
                NotificationResourceType.USER,
                11L,
                "friend-birthday:2026-10-20:11:d-8",
                LocalDateTime.of(2026, 10, 20, 15, 0)
        );
    }

    @Test
    void marchFirstOfNonLeapYear_createNotifications_includesLeapDayBirthdays() {
        given(friendRepository.findAllBirthdayNotificationTargets(3, 1, true)).willReturn(List.of());

        service.createNotifications(LocalDate.of(2027, 2, 21));

        verify(friendRepository).findAllBirthdayNotificationTargets(3, 1, true);
        verifyNoInteractions(notificationCreationService);
    }

    @Test
    void februaryTwentyNinthOfLeapYear_createNotifications_doesNotIncludeMarchFirstRule() {
        given(friendRepository.findAllBirthdayNotificationTargets(2, 29, false)).willReturn(List.of());

        service.createNotifications(LocalDate.of(2028, 2, 21));

        verify(friendRepository).findAllBirthdayNotificationTargets(2, 29, false);
    }
}
