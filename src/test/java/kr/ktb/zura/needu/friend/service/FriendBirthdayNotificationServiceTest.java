package kr.ktb.zura.needu.friend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FriendBirthdayNotificationServiceTest {

    private static final int CHUNK_SIZE = 2;

    private final FriendRepository friendRepository = org.mockito.Mockito.mock(FriendRepository.class);
    private final NotificationCreationService notificationCreationService =
            org.mockito.Mockito.mock(NotificationCreationService.class);
    private final FriendBirthdayNotificationService service = createService(CHUNK_SIZE);

    @Test
    void multipleOwnersAndFriends_createNotifications_groupsReceiversByBirthdayFriend() {
        given(friendRepository.findAllBirthdayNotificationTargets(List.of(1019))).willReturn(List.of(
                new FriendBirthdayNotificationTarget(1L, 10L, "수연"),
                new FriendBirthdayNotificationTarget(2L, 10L, "수연"),
                new FriendBirthdayNotificationTarget(3L, 11L, "민지")
        ));

        service.createNotifications(LocalDate.of(2026, 10, 12));

        verify(notificationCreationService).createNotifications(List.of(
                new NotificationCreationCommand(
                        Set.of(1L, 2L),
                        NotificationType.FRIEND_BIRTHDAY,
                        null,
                        "수연 님의 생일이 7일 남았어요.",
                        "지금 선물을 준비하면 여유 있게 도착해요.",
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
                        "지금 선물을 준비하면 여유 있게 도착해요.",
                        NotificationResourceType.USER,
                        11L,
                        "friend-birthday:2026-10-19:11:d-7",
                        LocalDateTime.of(2026, 10, 19, 15, 0)
                )
        ));
    }

    @Test
    void friendsExceedChunkSize_createNotifications_createsByChunk() {
        givenBirthdayFriends(10L, 11L, 12L);

        service.createNotifications(LocalDate.of(2026, 10, 12));

        assertThat(captureCreatedFriendUserIdChunks()).containsExactly(List.of(10L, 11L), List.of(12L));
    }

    @Test
    void chunkFailed_createNotifications_continuesWithNextChunk() {
        givenBirthdayFriends(10L, 11L, 12L);
        given(notificationCreationService.createNotifications(anyList()))
                .willThrow(new IllegalStateException("insert failed"))
                .willReturn(1);

        service.createNotifications(LocalDate.of(2026, 10, 12));

        assertThat(captureCreatedFriendUserIdChunks()).containsExactly(List.of(10L, 11L), List.of(12L));
    }

    @Test
    void nonPositiveChunkSize_create_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> createService(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chunk-size");
    }

    @Test
    void marchFirstOfNonLeapYear_createNotifications_includesLeapDayBirthdays() {
        given(friendRepository.findAllBirthdayNotificationTargets(List.of(301, 229))).willReturn(List.of());

        service.createNotifications(LocalDate.of(2027, 2, 22));

        verify(friendRepository).findAllBirthdayNotificationTargets(List.of(301, 229));
        verifyNoInteractions(notificationCreationService);
    }

    @Test
    void februaryTwentyNinthOfLeapYear_createNotifications_doesNotIncludeMarchFirstRule() {
        given(friendRepository.findAllBirthdayNotificationTargets(List.of(229))).willReturn(List.of());

        service.createNotifications(LocalDate.of(2028, 2, 22));

        verify(friendRepository).findAllBirthdayNotificationTargets(List.of(229));
    }

    private FriendBirthdayNotificationService createService(int chunkSize) {
        return new FriendBirthdayNotificationService(
                friendRepository, notificationCreationService, 7, "Asia/Seoul", chunkSize);
    }

    private void givenBirthdayFriends(Long... friendUserIds) {
        given(friendRepository.findAllBirthdayNotificationTargets(List.of(1019))).willReturn(Arrays.stream(friendUserIds)
                .map(friendUserId -> new FriendBirthdayNotificationTarget(1L, friendUserId, "친구" + friendUserId))
                .toList());
    }

    private List<List<Long>> captureCreatedFriendUserIdChunks() {
        ArgumentCaptor<List<NotificationCreationCommand>> chunkCaptor = ArgumentCaptor.forClass(List.class);
        verify(notificationCreationService, atLeastOnce()).createNotifications(chunkCaptor.capture());
        return chunkCaptor.getAllValues().stream()
                .map(chunk -> chunk.stream().map(NotificationCreationCommand::resourceId).toList())
                .toList();
    }
}
