package kr.ktb.zura.needu.friend.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.response.CursorPageResponse;
import kr.ktb.zura.needu.friend.dto.response.FriendResponse;
import kr.ktb.zura.needu.friend.dto.response.FriendSummaryResponse;
import kr.ktb.zura.needu.friend.entity.Friend;
import kr.ktb.zura.needu.friend.exception.FriendErrorCode;
import kr.ktb.zura.needu.friend.repository.FriendRepository;
import kr.ktb.zura.needu.user.dto.response.UserResponse;
import kr.ktb.zura.needu.user.dto.response.UserSummaryResponse;
import kr.ktb.zura.needu.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FriendService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

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

    public CursorPageResponse<FriendSummaryResponse> findAllFriends(Long userId, String cursor, int size) {
        userService.findUserSummary(userId);

        FriendCursor decodedCursor = cursor == null ? null : FriendCursor.decode(cursor);
        LocalDate referenceDate = decodedCursor == null ? LocalDate.now(KOREA_ZONE) : decodedCursor.referenceDate();
        int currentBirthdayKey = FriendCursor.birthdayKey(referenceDate);
        Limit limit = Limit.of(size + 1);
        List<FriendSummaryResponse> friends = decodedCursor == null
                ? friendRepository.findAllByOwnerUserIdOrderByUpcomingBirthday(userId, currentBirthdayKey, limit)
                : friendRepository.findAllByOwnerUserIdAfterBirthdayCursor(
                        userId, currentBirthdayKey, decodedCursor.sortKey(), decodedCursor.userId(), limit);

        boolean hasNext = friends.size() > size;
        List<FriendSummaryResponse> pageItems = hasNext ? friends.subList(0, size) : friends;
        String nextCursor = hasNext ? FriendCursor.from(referenceDate, pageItems.getLast()).encode() : null;
        return new CursorPageResponse<>(pageItems, nextCursor, hasNext);
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
