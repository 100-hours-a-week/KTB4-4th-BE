package kr.ktb.zura.needu.friend.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.friend.dto.response.FriendOverviewResponse;
import kr.ktb.zura.needu.friend.dto.response.FriendResponse;
import kr.ktb.zura.needu.friend.dto.response.FriendSummaryResponse;
import kr.ktb.zura.needu.friend.entity.Friend;
import kr.ktb.zura.needu.friend.exception.FriendErrorCode;
import kr.ktb.zura.needu.friend.repository.FriendRepository;
import kr.ktb.zura.needu.user.dto.response.UserResponse;
import kr.ktb.zura.needu.user.dto.response.UserSummaryResponse;
import kr.ktb.zura.needu.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FriendService {

    private final FriendRepository friendRepository;
    private final UserService userService;

    // 내 친구 목록에 없는 사용자는 존재 여부를 드러내지 않도록 권한 없음이 아닌 친구 없음으로 응답한다.
    public FriendResponse findFriend(Long ownerUserId, Long friendUserId) {
        if (!friendRepository.existsByOwnerUserIdAndFriendUserId(ownerUserId, friendUserId)) {
            throw new BusinessException(FriendErrorCode.FRIEND_NOT_FOUND);
        }
        try {
            UserResponse friendUser = userService.findAllUsers(List.of(friendUserId)).stream()
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(FriendErrorCode.FRIEND_NOT_FOUND));
            UserSummaryResponse friendSummary = userService.findUserSummary(friendUserId);
            return FriendResponse.from(friendUser, friendSummary);
        } catch (BusinessException exception) {
            throw new BusinessException(FriendErrorCode.FRIEND_NOT_FOUND);
        }
    }

    public FriendOverviewResponse findOverview(Long userId) {
        List<Friend> friends = friendRepository.findAllByOwnerUserIdOrderByIdAsc(userId);
        Map<Long, UserResponse> users = userService.findAllUsers(
                        friends.stream().map(Friend::getFriendUserId).toList()).stream()
                .collect(Collectors.toMap(UserResponse::id, Function.identity()));
        List<FriendSummaryResponse> items = friends.stream()
                .filter(friend -> users.containsKey(friend.getFriendUserId()))
                .map(friend -> toResponse(friend, users.get(friend.getFriendUserId())))
                .toList();
        return new FriendOverviewResponse(items, userService.isKakaoFriendSynced(userId));
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

    private FriendSummaryResponse toResponse(Friend friend, UserResponse user) {
        return new FriendSummaryResponse(user.id(), user.nickname(), user.profileImageUrl(), friend.isFavorite());
    }
}
