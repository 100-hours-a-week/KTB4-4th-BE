package kr.ktb.zura.needu.friend.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.response.CursorPageResponse;
import kr.ktb.zura.needu.friend.dto.request.FriendSearchCondition;
import kr.ktb.zura.needu.friend.dto.response.FriendDetailResponse;
import kr.ktb.zura.needu.friend.dto.response.FriendFavoriteResponse;
import kr.ktb.zura.needu.friend.dto.response.FriendListResponse;
import kr.ktb.zura.needu.friend.dto.response.FriendSummaryResponse;
import kr.ktb.zura.needu.friend.entity.Friend;
import kr.ktb.zura.needu.friend.exception.FriendErrorCode;
import kr.ktb.zura.needu.friend.repository.FriendRepository;
import kr.ktb.zura.needu.user.dto.response.UserDetailResponse;
import kr.ktb.zura.needu.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FriendService {

    private final FriendRepository friendRepository;
    private final UserService userService;

    public FriendDetailResponse findFriend(Long ownerUserId, Long friendUserId) {
        userService.validateActiveUser(ownerUserId);
        Friend friend = findFriendRelation(ownerUserId, friendUserId);
        UserDetailResponse friendUser = findActiveFriendUser(friendUserId);
        return FriendDetailResponse.of(friendUser, userService.findTasteProfile(friendUserId), friend.isFavorite());
    }

    public UserDetailResponse findFriendUser(Long ownerUserId, Long friendUserId) {
        if (!friendRepository.existsByOwnerUserIdAndFriendUserId(ownerUserId, friendUserId)) {
            throw new BusinessException(FriendErrorCode.FRIEND_NOT_FOUND);
        }
        return findActiveFriendUser(friendUserId);
    }

    @Transactional
    public FriendFavoriteResponse updateFavorite(Long ownerUserId, Long friendUserId, boolean isFavorite) {
        userService.validateActiveUser(ownerUserId);
        Friend friend = findFriendRelation(ownerUserId, friendUserId);
        findActiveFriendUser(friendUserId);
        if (isFavorite) {
            friend.markAsFavorite();
        } else {
            friend.unmarkAsFavorite();
        }
        return new FriendFavoriteResponse(friendUserId, friend.isFavorite());
    }

    public FriendListResponse findAllFriends(Long userId, FriendSearchCondition condition) {
        String keyword = normalizeKeyword(condition.keyword());
        userService.validateActiveUser(userId);
        if (keyword != null) {
            // TODO: 이름 검색과 조건에 묶인 커서를 구현
            throw new UnsupportedOperationException("FRD-1 미구현");
        }
        boolean isKakaoFriendSynced = userService.isKakaoFriendSynced(userId);

        String cursor = condition.cursor();
        int size = condition.size();
        boolean isFavoriteOnly = Boolean.TRUE.equals(condition.favorite());
        List<FriendSummaryResponse> friends = findAllByName(userId, isFavoriteOnly, cursor, Limit.of(size + 1));

        boolean hasNext = friends.size() > size;
        List<FriendSummaryResponse> pageItems = hasNext ? friends.subList(0, size) : friends;
        String nextCursor = hasNext ? FriendNameCursor.from(pageItems.getLast()).encode() : null;
        return FriendListResponse.from(
                new CursorPageResponse<>(pageItems, nextCursor, hasNext), isKakaoFriendSynced);
    }

    private Friend findFriendRelation(Long ownerUserId, Long friendUserId) {
        return friendRepository.findByOwnerUserIdAndFriendUserId(ownerUserId, friendUserId)
                .orElseThrow(() -> new BusinessException(FriendErrorCode.FRIEND_NOT_FOUND));
    }

    private UserDetailResponse findActiveFriendUser(Long friendUserId) {
        return userService.findUserById(friendUserId)
                .orElseThrow(() -> new BusinessException(FriendErrorCode.FRIEND_NOT_FOUND));
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null ? null : keyword.strip();
    }

    private List<FriendSummaryResponse> findAllByName(
            Long userId, boolean isFavoriteOnly, String cursor, Limit limit) {
        if (cursor == null) {
            return friendRepository.findAllByOwnerUserIdOrderByName(userId, isFavoriteOnly, limit);
        }
        FriendNameCursor decodedCursor = FriendNameCursor.decode(cursor);
        return friendRepository.findAllByOwnerUserIdAfterNameCursor(
                userId, isFavoriteOnly, decodedCursor.name(), decodedCursor.userId(), limit);
    }

    @Transactional
    public void syncKakaoFriends(Long ownerUserId, Collection<Long> kakaoFriendIds) {
        Map<Long, Long> userIdsByKakaoId = userService.findUserIdsByExternalIds(Set.copyOf(kakaoFriendIds));
        Set<Long> candidateUserIds = userIdsByKakaoId.values().stream()
                .filter(friendUserId -> !friendUserId.equals(ownerUserId))
                .collect(Collectors.toSet());
        Set<Long> existingFriendUserIds = candidateUserIds.isEmpty() ? Set.of()
                : friendRepository.findAllByOwnerUserIdAndFriendUserIdIn(ownerUserId, candidateUserIds).stream()
                        .map(Friend::getFriendUserId)
                        .collect(Collectors.toSet());
        List<Friend> newFriends = candidateUserIds.stream()
                .filter(friendUserId -> !existingFriendUserIds.contains(friendUserId))
                .map(friendUserId -> new Friend(ownerUserId, friendUserId))
                .toList();
        friendRepository.saveAll(newFriends);
        userService.completeKakaoFriendSync(ownerUserId);
    }
}
