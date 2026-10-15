package kr.ktb.zura.needu.friend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.friend.dto.request.FriendSearchCondition;
import kr.ktb.zura.needu.friend.dto.response.FriendDetailResponse;
import kr.ktb.zura.needu.friend.dto.response.FriendFavoriteResponse;
import kr.ktb.zura.needu.friend.dto.response.FriendListResponse;
import kr.ktb.zura.needu.friend.dto.response.FriendSummaryResponse;
import kr.ktb.zura.needu.friend.entity.Friend;
import kr.ktb.zura.needu.friend.exception.FriendErrorCode;
import kr.ktb.zura.needu.friend.repository.FriendRepository;
import kr.ktb.zura.needu.friend.repository.FriendSyncRequestRepository;
import kr.ktb.zura.needu.user.dto.response.AiSummaryResponse;
import kr.ktb.zura.needu.user.dto.response.TasteProfileResponse;
import kr.ktb.zura.needu.user.dto.response.UserDetailResponse;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import kr.ktb.zura.needu.user.service.UserService;
import kr.ktb.zura.needu.user.type.TasteProfileStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Limit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FriendServiceTest {

    private FriendRepository friendRepository;
    private FriendSyncRequestRepository friendSyncRequestRepository;
    private UserService userService;
    private FriendService friendService;

    @BeforeEach
    void setUp() {
        friendRepository = mock(FriendRepository.class);
        friendSyncRequestRepository = mock(FriendSyncRequestRepository.class);
        userService = mock(UserService.class);
        friendService = new FriendService(friendRepository, friendSyncRequestRepository, userService);
    }

    @Test
    void friendExists_findFriend_returnsFriendWithTasteProfileAndFavorite() {
        Friend friend = new Friend(1L, 2L);
        friend.markAsFavorite();
        when(friendRepository.findByOwnerUserIdAndFriendUserId(1L, 2L)).thenReturn(Optional.of(friend));
        when(userService.findUserById(2L))
                .thenReturn(Optional.of(new UserDetailResponse(
                        2L, "친구", null, true, LocalDate.of(2000, 2, 29))));
        when(userService.findTasteProfile(2L)).thenReturn(new TasteProfileResponse(
                TasteProfileStatus.COMPLETED, List.of("미니멀"), List.of("러닝"), AiSummaryResponse.from("러닝을 좋아해요.")));

        FriendDetailResponse response = friendService.findFriend(1L, 2L);

        assertThat(response).isEqualTo(new FriendDetailResponse(
                2L, "친구", null, LocalDate.of(2000, 2, 29), true,
                List.of("미니멀"), List.of("러닝"), "러닝을 좋아해요.", true));
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
        when(friendRepository.findByOwnerUserIdAndFriendUserId(1L, 2L))
                .thenReturn(Optional.of(new Friend(1L, 2L)));
        when(userService.findUserById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> friendService.findFriend(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(FriendErrorCode.FRIEND_NOT_FOUND);
    }

    @Test
    void blockedLoginUser_findFriend_throwsUserBlockedWithoutFindingFriend() {
        doThrow(new BusinessException(UserErrorCode.USER_BLOCKED)).when(userService).validateActiveUser(1L);

        assertThatThrownBy(() -> friendService.findFriend(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(UserErrorCode.USER_BLOCKED);
        verify(friendRepository, never()).findByOwnerUserIdAndFriendUserId(1L, 2L);
    }

    @Test
    void friendExists_updateFavorite_marksAndUnmarksFavorite() {
        Friend friend = new Friend(1L, 2L);
        when(friendRepository.findByOwnerUserIdAndFriendUserId(1L, 2L)).thenReturn(Optional.of(friend));
        when(userService.findUserById(2L))
                .thenReturn(Optional.of(new UserDetailResponse(2L, "친구", null, true, null)));

        FriendFavoriteResponse marked = friendService.updateFavorite(1L, 2L, true);
        FriendFavoriteResponse markedAgain = friendService.updateFavorite(1L, 2L, true);

        assertThat(marked).isEqualTo(new FriendFavoriteResponse(2L, true));
        assertThat(markedAgain).isEqualTo(marked);
        assertThat(friendService.updateFavorite(1L, 2L, false)).isEqualTo(new FriendFavoriteResponse(2L, false));
        assertThat(friend.isFavorite()).isFalse();
    }

    @Test
    void notFriend_updateFavorite_throwsFriendNotFound() {
        assertThatThrownBy(() -> friendService.updateFavorite(1L, 2L, true))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(FriendErrorCode.FRIEND_NOT_FOUND);
    }

    @Test
    void missingSort_findAllFriends_returnsNamePageAndCursor() {
        FriendSummaryResponse first = friendSummary(2L, "가나", LocalDate.of(2000, 10, 1));
        FriendSummaryResponse second = friendSummary(3L, "다라", LocalDate.of(2000, 10, 2));
        FriendSummaryResponse extra = friendSummary(4L, "마바", LocalDate.of(2000, 10, 3));
        when(friendRepository.findAllByOwnerUserIdOrderByName(1L, false, "%", Limit.of(3)))
                .thenReturn(List.of(first, second, extra));
        when(friendRepository.countAllActiveByOwnerUserId(1L)).thenReturn(12L);
        when(friendRepository.countAllActiveFavoritesByOwnerUserId(1L)).thenReturn(4L);
        when(userService.isKakaoFriendSynced(1L)).thenReturn(true);

        FriendListResponse response = friendService.findAllFriends(
                1L, new FriendSearchCondition(null, null, null, 2));

        assertThat(response.items()).containsExactly(first, second);
        assertThat(response.isKakaoFriendSynced()).isTrue();
        assertThat(response.totalCount()).isEqualTo(12L);
        assertThat(response.favoriteTotalCount()).isEqualTo(4L);
        assertThat(response.hasNext()).isTrue();
        assertThat(FriendNameCursor.decode(response.nextCursor()))
                .isEqualTo(new FriendNameCursor("다라", 3L));
        verify(userService).validateActiveUser(1L);
    }

    @Test
    void favoriteFalseWithCursor_findAllFriends_returnsAllFriendsAfterNameCursor() {
        FriendSummaryResponse favorite = new FriendSummaryResponse(4L, "마바", null, null, true);
        FriendSummaryResponse regular = friendSummary(5L, "사아", null);
        String cursor = new FriendNameCursor("다라", 3L).encode();
        when(friendRepository.findAllByOwnerUserIdAfterNameCursor(1L, false, "%", "다라", 3L, Limit.of(3)))
                .thenReturn(List.of(favorite, regular));

        FriendListResponse response = friendService.findAllFriends(
                1L, new FriendSearchCondition(false, null, cursor, 2));

        assertThat(response.items()).containsExactly(favorite, regular);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void favoriteTrue_findAllFriends_returnsFavoriteNamePageAndCursor() {
        FriendSummaryResponse first = new FriendSummaryResponse(2L, "가나", null, null, true);
        FriendSummaryResponse extra = new FriendSummaryResponse(3L, "다라", null, null, true);
        when(friendRepository.findAllByOwnerUserIdOrderByName(1L, true, "%", Limit.of(2)))
                .thenReturn(List.of(first, extra));

        FriendListResponse response = friendService.findAllFriends(
                1L, new FriendSearchCondition(true, null, null, 1));

        assertThat(response.items()).containsExactly(first);
        assertThat(response.hasNext()).isTrue();
        assertThat(FriendNameCursor.decode(response.nextCursor())).isEqualTo(new FriendNameCursor("가나", 2L));
    }

    @Test
    void favoriteTrueWithCursor_findAllFriends_queriesFavoritesAfterNameCursor() {
        FriendSummaryResponse favorite = new FriendSummaryResponse(3L, "다라", null, null, true);
        String cursor = new FriendNameCursor("가나", 2L).encode();
        when(friendRepository.findAllByOwnerUserIdAfterNameCursor(1L, true, "%", "가나", 2L, Limit.of(2)))
                .thenReturn(List.of(favorite));

        FriendListResponse response = friendService.findAllFriends(
                1L, new FriendSearchCondition(true, null, cursor, 1));

        assertThat(response.items()).containsExactly(favorite);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void keyword_findAllFriends_queriesStrippedNamePrefixPattern() {
        FriendSummaryResponse friend = friendSummary(2L, "김철수", null);
        when(friendRepository.findAllByOwnerUserIdOrderByName(1L, false, "김%", Limit.of(3)))
                .thenReturn(List.of(friend));

        FriendListResponse response = friendService.findAllFriends(
                1L, new FriendSearchCondition(null, " 김 ", null, 2));

        assertThat(response.items()).containsExactly(friend);
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    void keywordWithLikeWildcards_findAllFriends_escapesWildcards() {
        when(friendRepository.findAllByOwnerUserIdOrderByName(1L, true, "50!%!_!!%", Limit.of(3)))
                .thenReturn(List.of());

        friendService.findAllFriends(1L, new FriendSearchCondition(true, "50%_!", null, 2));

        verify(friendRepository).findAllByOwnerUserIdOrderByName(1L, true, "50!%!_!!%", Limit.of(3));
    }

    @Test
    void keywordWithCursor_findAllFriends_queriesPatternAfterNameCursor() {
        String cursor = new FriendNameCursor("김수지", 2L).encode();
        when(friendRepository.findAllByOwnerUserIdAfterNameCursor(1L, false, "김%", "김수지", 2L, Limit.of(3)))
                .thenReturn(List.of());

        FriendListResponse response = friendService.findAllFriends(
                1L, new FriendSearchCondition(null, "김", cursor, 2));

        assertThat(response.items()).isEmpty();
        verify(friendRepository).findAllByOwnerUserIdAfterNameCursor(1L, false, "김%", "김수지", 2L, Limit.of(3));
    }

    @Test
    void lastPage_findAllFriends_returnsNullCursor() {
        FriendSummaryResponse friend = friendSummary(2L, LocalDate.of(2000, 10, 1));
        when(friendRepository.findAllByOwnerUserIdOrderByName(1L, false, "%", Limit.of(3)))
                .thenReturn(List.of(friend));

        FriendListResponse response = friendService.findAllFriends(
                1L, new FriendSearchCondition(null, null, null, 2));

        assertThat(response.items()).containsExactly(friend);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void newKakaoFriends_syncKakaoFriends_savesOwnerRelationsOnly() {
        LocalDateTime beforeSync = LocalDateTime.now(ZoneOffset.UTC);
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
        ArgumentCaptor<LocalDateTime> requestedAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(friendSyncRequestRepository).createIfAbsent(eq(3L), requestedAtCaptor.capture());
        assertThat(requestedAtCaptor.getValue())
                .isBetween(beforeSync, LocalDateTime.now(ZoneOffset.UTC));
        verify(userService).completeKakaoFriendSync(1L);
    }

    @Test
    void emptyKakaoFriends_syncKakaoFriends_marksSyncComplete() {
        when(userService.findUserIdsByExternalIds(java.util.Set.of())).thenReturn(Map.of());

        friendService.syncKakaoFriends(1L, List.of());

        verify(friendSyncRequestRepository, never()).createIfAbsent(any(), any());
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

        verify(friendSyncRequestRepository, never()).createIfAbsent(any(), any());
        verify(userService, never()).completeKakaoFriendSync(1L);
    }

    @Test
    void syncRequestSaveFailure_syncKakaoFriends_doesNotMarkSyncComplete() {
        when(userService.findUserIdsByExternalIds(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(Map.of(20L, 2L));
        when(friendRepository.findAllByOwnerUserIdAndFriendUserIdIn(1L, java.util.Set.of(2L)))
                .thenReturn(List.of());
        doThrow(new IllegalStateException()).when(friendSyncRequestRepository)
                .createIfAbsent(eq(2L), any());

        assertThatThrownBy(() -> friendService.syncKakaoFriends(1L, List.of(20L)))
                .isInstanceOf(IllegalStateException.class);

        verify(userService, never()).completeKakaoFriendSync(1L);
    }

    @Test
    void newKakaoFriends_syncRequestedKakaoFriends_savesRelationsAndReturnsNewFriendUserIds() {
        when(userService.findUserIdsByExternalIds(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(Map.of(20L, 2L, 30L, 3L));
        when(friendRepository.findAllByOwnerUserIdAndFriendUserIdIn(1L, java.util.Set.of(2L, 3L)))
                .thenReturn(List.of(new Friend(1L, 3L)));

        List<Long> newFriendUserIds = friendService.syncRequestedKakaoFriends(1L, List.of(20L, 30L));

        assertThat(newFriendUserIds).containsExactly(2L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Friend>> captor = ArgumentCaptor.forClass(List.class);
        verify(friendRepository).saveAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(Friend::getOwnerUserId, Friend::getFriendUserId)
                .containsExactly(tuple(1L, 2L));
        verify(friendSyncRequestRepository, never()).createIfAbsent(any(), any());
        verify(userService).completeKakaoFriendSync(1L);
    }

    @Test
    void confirmedActiveFriends_findAllFriendNicknames_returnsNicknamesByFriendUserId() {
        when(friendRepository.findAllByOwnerUserIdAndFriendUserIdIn(1L, List.of(2L, 3L, 4L)))
                .thenReturn(List.of(new Friend(1L, 2L), new Friend(1L, 3L)));
        // 3L은 친구지만 탈퇴·차단 등으로 활성 사용자 조회에서 빠진 경우
        when(userService.findAllActiveUsersByIds(List.of(2L, 3L)))
                .thenReturn(List.of(new UserDetailResponse(2L, "수연", null, true, null)));

        Map<Long, String> nicknames = friendService.findAllFriendNicknames(1L, List.of(2L, 3L, 4L));

        assertThat(nicknames).containsExactly(Map.entry(2L, "수연"));
    }

    @Test
    void noFriendRelation_findAllFriendNicknames_returnsEmptyWithoutUserLookup() {
        when(friendRepository.findAllByOwnerUserIdAndFriendUserIdIn(1L, List.of(4L))).thenReturn(List.of());

        Map<Long, String> nicknames = friendService.findAllFriendNicknames(1L, List.of(4L));

        assertThat(nicknames).isEmpty();
        verify(userService, never()).findAllActiveUsersByIds(any());
    }

    @Test
    void emptyFriendUserIds_findAllFriendNicknames_returnsEmptyWithoutQuery() {
        Map<Long, String> nicknames = friendService.findAllFriendNicknames(1L, List.of());

        assertThat(nicknames).isEmpty();
        verify(friendRepository, never()).findAllByOwnerUserIdAndFriendUserIdIn(any(), any());
    }

    private FriendSummaryResponse friendSummary(Long userId, LocalDate birthDate) {
        return friendSummary(userId, "친구", birthDate);
    }

    private FriendSummaryResponse friendSummary(Long userId, String name, LocalDate birthDate) {
        return new FriendSummaryResponse(userId, name, null, birthDate, false);
    }
}
