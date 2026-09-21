package kr.ktb.zura.needu.friend.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.response.CursorPageResponse;
import kr.ktb.zura.needu.friend.dto.response.FriendDetailResponse;
import kr.ktb.zura.needu.friend.dto.response.FriendSummaryResponse;
import kr.ktb.zura.needu.friend.entity.Friend;
import kr.ktb.zura.needu.friend.exception.FriendErrorCode;
import kr.ktb.zura.needu.friend.repository.FriendRepository;
import kr.ktb.zura.needu.user.dto.response.UserDetailResponse;
import kr.ktb.zura.needu.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Limit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FriendServiceTest {

    private FriendRepository friendRepository;
    private UserService userService;
    private FriendService friendService;

    @BeforeEach
    void setUp() {
        friendRepository = mock(FriendRepository.class);
        userService = mock(UserService.class);
        friendService = new FriendService(friendRepository, userService);
    }

    @Test
    void friendExists_findFriend_returnsFriendUser() {
        when(friendRepository.existsByOwnerUserIdAndFriendUserId(1L, 2L)).thenReturn(true);
        when(userService.findUserById(2L))
                .thenReturn(Optional.of(new UserDetailResponse(
                        2L, "친구", null, true, LocalDate.of(2000, 2, 29))));

        FriendDetailResponse response = friendService.findFriend(1L, 2L);

        assertThat(response).isEqualTo(new FriendDetailResponse(2L, "친구", null, true, LocalDate.of(2000, 2, 29)));
    }

    @Test
    void notFriend_findFriend_throwsFriendNotFound() {
        assertThatThrownBy(() -> friendService.findFriend(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(FriendErrorCode.FRIEND_NOT_FOUND);
        verify(userService, never()).findUserById(2L);
    }

    @Test
    void missingUser_findFriend_throwsFriendNotFound() {
        when(friendRepository.existsByOwnerUserIdAndFriendUserId(1L, 2L)).thenReturn(true);
        when(userService.findUserById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> friendService.findFriend(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(FriendErrorCode.FRIEND_NOT_FOUND);
    }

    @Test
    void moreFriendsThanSize_findAllFriends_returnsPageAndCursor() {
        FriendSummaryResponse first = friendSummary(2L, LocalDate.of(2000, 10, 1));
        FriendSummaryResponse second = friendSummary(3L, LocalDate.of(2000, 10, 2));
        FriendSummaryResponse extra = friendSummary(4L, LocalDate.of(2000, 10, 3));
        when(friendRepository.findAllByOwnerUserIdOrderByUpcomingBirthday(eq(1L), anyInt(), eq(Limit.of(3))))
                .thenReturn(List.of(first, second, extra));

        CursorPageResponse<FriendSummaryResponse> response = friendService.findAllFriends(1L, null, 2);

        assertThat(response.items()).containsExactly(first, second);
        assertThat(response.hasNext()).isTrue();
        assertThat(FriendCursor.decode(response.nextCursor()).userId()).isEqualTo(3L);
        verify(userService).findUserSummary(1L);
    }

    @Test
    void lastPage_findAllFriends_returnsNullCursor() {
        FriendSummaryResponse friend = friendSummary(2L, LocalDate.of(2000, 10, 1));
        when(friendRepository.findAllByOwnerUserIdOrderByUpcomingBirthday(eq(1L), anyInt(), eq(Limit.of(3))))
                .thenReturn(List.of(friend));

        CursorPageResponse<FriendSummaryResponse> response = friendService.findAllFriends(1L, null, 2);

        assertThat(response.items()).containsExactly(friend);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void newKakaoFriends_syncKakaoFriends_savesOwnerRelationsOnly() {
        when(userService.findUserIdsByExternalIds(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(Map.of(10L, 1L, 20L, 2L, 30L, 3L));
        when(friendRepository.findAllByOwnerUserIdAndFriendUserIdIn(1L, java.util.Set.of(2L, 3L)))
                .thenReturn(List.of(new Friend(1L, 2L)));

        friendService.syncKakaoFriends(1L, List.of(20L, 30L, 10L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Friend>> captor = ArgumentCaptor.forClass(List.class);
        verify(friendRepository).saveAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(Friend::getOwnerUserId, Friend::getFriendUserId)
                .containsExactly(tuple(1L, 3L));
        verify(userService).completeKakaoFriendSync(1L);
    }

    @Test
    void emptyKakaoFriends_syncKakaoFriends_marksSyncComplete() {
        when(userService.findUserIdsByExternalIds(java.util.Set.of())).thenReturn(Map.of());

        friendService.syncKakaoFriends(1L, List.of());

        verify(userService).completeKakaoFriendSync(1L);
    }

    @Test
    void friendSaveFailure_syncKakaoFriends_doesNotMarkSyncComplete() {
        when(userService.findUserIdsByExternalIds(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(Map.of(20L, 2L));
        when(friendRepository.findAllByOwnerUserIdAndFriendUserIdIn(1L, java.util.Set.of(2L)))
                .thenReturn(List.of());
        doThrow(new IllegalStateException()).when(friendRepository).saveAll(anyList());

        assertThatThrownBy(() -> friendService.syncKakaoFriends(1L, List.of(20L)))
                .isInstanceOf(IllegalStateException.class);

        verify(userService, never()).completeKakaoFriendSync(1L);
    }

    private FriendSummaryResponse friendSummary(Long userId, LocalDate birthDate) {
        return new FriendSummaryResponse(userId, "친구", null, birthDate, false);
    }
}
