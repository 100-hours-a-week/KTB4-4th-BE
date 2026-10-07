package kr.ktb.zura.needu.friend.service;

import java.time.LocalDate;
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
    void keywordOverTwentyCharactersAfterStrip_findAllFriends_throwsInvalidInput() {
        FriendSearchCondition condition = new FriendSearchCondition(null, null, "  " + "가".repeat(21) + "  ", null, 20);

        assertThatThrownBy(() -> friendService.findAllFriends(1L, condition))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(CommonErrorCode.COMMON_INVALID_INPUT);
    }

    @Test
    void missingSort_findAllFriends_returnsNamePageAndCursor() {
        FriendSummaryResponse first = friendSummary(2L, "가나", LocalDate.of(2000, 10, 1));
        FriendSummaryResponse second = friendSummary(3L, "다라", LocalDate.of(2000, 10, 2));
        FriendSummaryResponse extra = friendSummary(4L, "마바", LocalDate.of(2000, 10, 3));
        when(friendRepository.findAllByOwnerUserIdOrderByName(1L, Limit.of(3)))
                .thenReturn(List.of(first, second, extra));
        when(userService.isKakaoFriendSynced(1L)).thenReturn(true);

        FriendListResponse response = friendService.findAllFriends(
                1L, new FriendSearchCondition(null, null, null, null, 2));

        assertThat(response.items()).containsExactly(first, second);
        assertThat(response.isKakaoFriendSynced()).isTrue();
        assertThat(response.hasNext()).isTrue();
        assertThat(FriendNameCursor.decode(response.nextCursor()))
                .isEqualTo(new FriendNameCursor("다라", 3L));
        verify(userService).validateActiveUser(1L);
    }

    @Test
    void lastPage_findAllFriends_returnsNullCursor() {
        FriendSummaryResponse friend = friendSummary(2L, LocalDate.of(2000, 10, 1));
        when(friendRepository.findAllByOwnerUserIdOrderByUpcomingBirthday(eq(1L), anyInt(), eq(Limit.of(3))))
                .thenReturn(List.of(friend));

        FriendListResponse response = friendService.findAllFriends(
                1L, new FriendSearchCondition("birthday", null, null, null, 2));

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
        return friendSummary(userId, "친구", birthDate);
    }

    private FriendSummaryResponse friendSummary(Long userId, String name, LocalDate birthDate) {
        return new FriendSummaryResponse(userId, name, null, birthDate, false);
    }
}
