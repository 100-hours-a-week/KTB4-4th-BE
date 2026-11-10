package kr.ktb.zura.needu.friend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import kr.ktb.zura.needu.friend.dto.response.FriendBirthdayNotificationTarget;
import kr.ktb.zura.needu.friend.repository.FriendRepository;
import kr.ktb.zura.needu.notification.service.NotificationCreationCommand;
import kr.ktb.zura.needu.notification.service.NotificationCreationService;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationType;
import kr.ktb.zura.needu.user.dto.response.UserSummaryResponse;
import kr.ktb.zura.needu.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FriendBirthdayNotificationServiceTest {

    private static final int CHUNK_SIZE = 2;
    private static final List<Integer> REMINDER_DAYS = List.of(7, 5, 3, 1, 0);
    private static final List<Integer> OCTOBER_19 = List.of(1019);

    private final FriendRepository friendRepository = org.mockito.Mockito.mock(FriendRepository.class);
    private final UserService userService = org.mockito.Mockito.mock(UserService.class);
    private final NotificationCreationService notificationCreationService =
            org.mockito.Mockito.mock(NotificationCreationService.class);
    private final FriendBirthdayNotificationService service = createService(CHUNK_SIZE);

    @Test
    void multipleOwnersAndFriends_createNotifications_groupsReceiversByBirthdayFriend() {
        given(userService.findAllActiveUsersByBirthMonthDays(OCTOBER_19, 0L, CHUNK_SIZE)).willReturn(List.of(
                birthdayFriend(10L, "수연"),
                birthdayFriend(11L, "민지")
        ));
        given(friendRepository.findAllBirthdayNotificationTargets(List.of(10L, 11L))).willReturn(List.of(
                new FriendBirthdayNotificationTarget(1L, 10L),
                new FriendBirthdayNotificationTarget(2L, 10L),
                new FriendBirthdayNotificationTarget(3L, 11L)
        ));

        service.createNotifications(LocalDate.of(2026, 10, 12));

        verify(notificationCreationService).createNotifications(List.of(
                new NotificationCreationCommand(
                        Set.of(1L, 2L),
                        NotificationType.FRIEND_BIRTHDAY,
                        null,
                        "수연 님의 생일이 7일 남았어요.",
                        "미리 취향에 맞는 선물을 골라 여유 있게 준비해 보세요.",
                        NotificationResourceType.USER,
                        10L,
                        "friend-birthday:2026-10-19:10:d-7",
                        LocalDateTime.of(2026, 10, 19, 15, 0)
                ),
                new NotificationCreationCommand(
                        Set.of(3L),
                        NotificationType.FRIEND_BIRTHDAY,
                        null,
                        "민지 님의 생일이 7일 남았어요.",
                        "미리 취향에 맞는 선물을 골라 여유 있게 준비해 보세요.",
                        NotificationResourceType.USER,
                        11L,
                        "friend-birthday:2026-10-19:11:d-7",
                        LocalDateTime.of(2026, 10, 19, 15, 0)
                )
        ));
    }

    @Test
    void friendsExceedChunkSize_createNotifications_findsAndCreatesByChunk() {
        givenBirthdayFriendPage(0L, 10L, 11L);
        givenBirthdayFriendPage(11L, 12L);

        service.createNotifications(LocalDate.of(2026, 10, 12));

        assertThat(captureCreatedFriendUserIdChunks()).containsExactly(List.of(10L, 11L), List.of(12L));
        verify(friendRepository).findAllBirthdayNotificationTargets(List.of(10L, 11L));
        verify(friendRepository).findAllBirthdayNotificationTargets(List.of(12L));
    }

    @Test
    void multipleReminderDays_createNotifications_createsEachReminderNotification() {
        givenReminderDayTarget(1019, 10L, "친구10");
        givenReminderDayTarget(1017, 11L, "친구11");
        givenReminderDayTarget(1015, 12L, "친구12");
        givenReminderDayTarget(1013, 13L, "친구13");
        givenReminderDayTarget(1012, 14L, "친구14");

        service.createNotifications(LocalDate.of(2026, 10, 12));

        ArgumentCaptor<List<NotificationCreationCommand>> commandCaptor = ArgumentCaptor.forClass(List.class);
        verify(notificationCreationService, atLeastOnce()).createNotifications(commandCaptor.capture());
        assertThat(commandCaptor.getAllValues().stream().map(List::getFirst).toList())
                .extracting(
                        NotificationCreationCommand::title,
                        NotificationCreationCommand::body,
                        NotificationCreationCommand::dedupKey,
                        NotificationCreationCommand::expiresAt)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "친구10 님의 생일이 7일 남았어요.",
                                "미리 취향에 맞는 선물을 골라 여유 있게 준비해 보세요.",
                                "friend-birthday:2026-10-19:10:d-7",
                                LocalDateTime.of(2026, 10, 19, 15, 0)),
                        org.assertj.core.groups.Tuple.tuple(
                                "친구11 님의 생일이 5일 남았어요.",
                                "선물을 고민 중이라면 지금 추천 상품을 확인해 보세요.",
                                "friend-birthday:2026-10-17:11:d-5",
                                LocalDateTime.of(2026, 10, 17, 15, 0)),
                        org.assertj.core.groups.Tuple.tuple(
                                "친구12 님의 생일이 3일 남았어요.",
                                "생일이 가까워졌어요. 마음에 드는 선물을 지금 골라보세요.",
                                "friend-birthday:2026-10-15:12:d-3",
                                LocalDateTime.of(2026, 10, 15, 15, 0)),
                        org.assertj.core.groups.Tuple.tuple(
                                "친구13 님의 생일이 하루 남았어요.",
                                "늦기 전에 취향에 맞는 선물을 준비해 보세요.",
                                "friend-birthday:2026-10-13:13:d-1",
                                LocalDateTime.of(2026, 10, 13, 15, 0)),
                        org.assertj.core.groups.Tuple.tuple(
                                "친구14 님의 생일이에요.",
                                "오늘 마음을 담은 선물로 생일을 축하해 주세요.",
                                "friend-birthday:2026-10-12:14:d-0",
                                LocalDateTime.of(2026, 10, 12, 15, 0)));
    }

    @Test
    void lastChunkIsFull_createNotifications_stopsAtEmptyPage() {
        givenBirthdayFriendPage(0L, 10L, 11L);
        given(userService.findAllActiveUsersByBirthMonthDays(OCTOBER_19, 11L, CHUNK_SIZE)).willReturn(List.of());

        service.createNotifications(LocalDate.of(2026, 10, 12));

        assertThat(captureCreatedFriendUserIdChunks()).containsExactly(List.of(10L, 11L));
        verify(userService).findAllActiveUsersByBirthMonthDays(OCTOBER_19, 11L, CHUNK_SIZE);
    }

    @Test
    void chunkWithoutReceivers_createNotifications_skipsChunkAndContinues() {
        given(userService.findAllActiveUsersByBirthMonthDays(OCTOBER_19, 0L, CHUNK_SIZE)).willReturn(List.of(
                birthdayFriend(10L, "친구10"),
                birthdayFriend(11L, "친구11")
        ));
        given(friendRepository.findAllBirthdayNotificationTargets(List.of(10L, 11L))).willReturn(List.of());
        givenBirthdayFriendPage(11L, 12L);

        service.createNotifications(LocalDate.of(2026, 10, 12));

        assertThat(captureCreatedFriendUserIdChunks()).containsExactly(List.of(12L));
    }

    @Test
    void chunkFailed_createNotifications_continuesWithNextChunkAndThrowsAfterBatch() {
        givenBirthdayFriendPage(0L, 10L, 11L);
        givenBirthdayFriendPage(11L, 12L);
        given(notificationCreationService.createNotifications(anyList()))
                .willThrow(new IllegalStateException("insert failed"))
                .willReturn(1);

        assertThatThrownBy(() -> service.createNotifications(LocalDate.of(2026, 10, 12)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("partially failed")
                .hasCauseInstanceOf(IllegalStateException.class);

        assertThat(captureCreatedFriendUserIdChunks()).containsExactly(List.of(10L, 11L), List.of(12L));
    }

    @Test
    void birthdayFriendLookupFailed_createNotifications_propagatesFailure() {
        given(userService.findAllActiveUsersByBirthMonthDays(OCTOBER_19, 0L, CHUNK_SIZE))
                .willThrow(new IllegalStateException("lookup failed"));

        assertThatThrownBy(() -> service.createNotifications(LocalDate.of(2026, 10, 12)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("lookup failed");

        verifyNoInteractions(friendRepository, notificationCreationService);
    }

    @Test
    void nonPositiveChunkSize_create_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> createService(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chunk-size");
    }

    @Test
    void marchFirstOfNonLeapYear_createNotifications_includesLeapDayBirthdays() {
        given(userService.findAllActiveUsersByBirthMonthDays(List.of(301, 229), 0L, CHUNK_SIZE)).willReturn(List.of());

        service.createNotifications(LocalDate.of(2027, 2, 22));

        verify(userService).findAllActiveUsersByBirthMonthDays(List.of(301, 229), 0L, CHUNK_SIZE);
        verify(friendRepository, never()).findAllBirthdayNotificationTargets(anyList());
        verifyNoInteractions(notificationCreationService);
    }

    @Test
    void februaryTwentyNinthOfLeapYear_createNotifications_doesNotIncludeMarchFirstRule() {
        given(userService.findAllActiveUsersByBirthMonthDays(List.of(229), 0L, CHUNK_SIZE)).willReturn(List.of());

        service.createNotifications(LocalDate.of(2028, 2, 22));

        verify(userService).findAllActiveUsersByBirthMonthDays(List.of(229), 0L, CHUNK_SIZE);
    }

    private FriendBirthdayNotificationService createService(int chunkSize) {
        return new FriendBirthdayNotificationService(
                friendRepository, userService, notificationCreationService, REMINDER_DAYS, "Asia/Seoul", chunkSize);
    }

    private void givenReminderDayTarget(int birthMonthDay, Long friendUserId, String nickname) {
        given(userService.findAllActiveUsersByBirthMonthDays(List.of(birthMonthDay), 0L, CHUNK_SIZE))
                .willReturn(List.of(birthdayFriend(friendUserId, nickname)));
        given(friendRepository.findAllBirthdayNotificationTargets(List.of(friendUserId)))
                .willReturn(List.of(new FriendBirthdayNotificationTarget(1L, friendUserId)));
    }

    // cursorUserId 다음 페이지로 주어진 생일 친구를 돌려주고, 친구마다 수신자 1명을 둔다
    private void givenBirthdayFriendPage(Long cursorUserId, Long... friendUserIds) {
        given(userService.findAllActiveUsersByBirthMonthDays(OCTOBER_19, cursorUserId, CHUNK_SIZE))
                .willReturn(Arrays.stream(friendUserIds)
                        .map(friendUserId -> birthdayFriend(friendUserId, "친구" + friendUserId))
                        .toList());
        given(friendRepository.findAllBirthdayNotificationTargets(List.of(friendUserIds)))
                .willReturn(Arrays.stream(friendUserIds)
                        .map(friendUserId -> new FriendBirthdayNotificationTarget(1L, friendUserId))
                        .toList());
    }

    private UserSummaryResponse birthdayFriend(Long userId, String nickname) {
        return new UserSummaryResponse(userId, nickname, LocalDate.of(2000, 10, 19), false);
    }

    private List<List<Long>> captureCreatedFriendUserIdChunks() {
        ArgumentCaptor<List<NotificationCreationCommand>> chunkCaptor = ArgumentCaptor.forClass(List.class);
        verify(notificationCreationService, atLeastOnce()).createNotifications(chunkCaptor.capture());
        return chunkCaptor.getAllValues().stream()
                .map(chunk -> chunk.stream().map(NotificationCreationCommand::resourceId).toList())
                .toList();
    }
}
