package kr.ktb.zura.needu.friend.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.friend.dto.response.FriendOverviewResponse;
import kr.ktb.zura.needu.friend.dto.response.FriendResponse;
import kr.ktb.zura.needu.friend.entity.Friend;
import kr.ktb.zura.needu.friend.repository.FriendRepository;
import kr.ktb.zura.needu.user.dto.response.UserResponse;
import kr.ktb.zura.needu.user.dto.response.UserSummaryResponse;
import kr.ktb.zura.needu.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyList;
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
        when(userService.findAllUsers(List.of(2L)))
                .thenReturn(List.of(new UserResponse(2L, 20L, "친구", null, true)));
        when(userService.findUserSummary(2L))
                .thenReturn(new UserSummaryResponse(2L, "친구", LocalDate.now(), true));

        FriendResponse response = friendService.findFriend(1L, 2L);

        assertThat(response).isEqualTo(new FriendResponse(2L, "친구", null, true));
    }

    @Test
    void notFriend_findFriend_throwsFriendNotFound() {
        assertThatThrownBy(() -> friendService.findFriend(1L, 2L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void unsyncedUser_findOverview_returnsStatusWithExistingFriends() {
        Friend friend = new Friend(1L, 2L);
        when(friendRepository.findAllByOwnerUserIdOrderByIdAsc(1L)).thenReturn(List.of(friend));
        when(userService.findAllUsers(List.of(2L)))
                .thenReturn(List.of(new UserResponse(2L, 20L, "친구", null, true)));

        FriendOverviewResponse response = friendService.findOverview(1L);

        assertThat(response.kakaoFriendSynced()).isFalse();
        assertThat(response.items().getFirst().userId()).isEqualTo(2L);
    }

    @Test
    void missingKakaoFriendRelations_syncKakaoFriends_savesBothDirections() {
        when(userService.findUserIdsByExternalIds(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(Map.of(10L, 1L, 20L, 2L, 30L, 3L));
        when(friendRepository.findAllByOwnerUserIdAndFriendUserIdIn(1L, java.util.Set.of(2L, 3L)))
                .thenReturn(List.of(new Friend(1L, 2L)));
        when(friendRepository.findAllByOwnerUserIdInAndFriendUserId(java.util.Set.of(2L, 3L), 1L))
                .thenReturn(List.of(new Friend(3L, 1L)));

        friendService.syncKakaoFriends(1L, List.of(20L, 30L, 10L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Friend>> captor = ArgumentCaptor.forClass(List.class);
        verify(friendRepository).saveAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(Friend::getOwnerUserId, Friend::getFriendUserId)
                .containsExactlyInAnyOrder(tuple(1L, 3L), tuple(2L, 1L));
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
}
