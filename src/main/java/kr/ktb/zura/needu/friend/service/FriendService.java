package kr.ktb.zura.needu.friend.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
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

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final int MAX_KEYWORD_LENGTH = 20;

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
        if (Boolean.TRUE.equals(condition.favorite()) || keyword != null) {
            // TODO: 즐겨찾기 필터, 이름 검색, V2 정렬(즐겨찾기 → 다가오는 생일 → 이름)과 조건에 묶인 커서를 구현
            throw new UnsupportedOperationException("FRD-1 미구현");
        }
        boolean isKakaoFriendSynced = userService.isKakaoFriendSynced(userId);

        String sort = condition.sort();
        String cursor = condition.cursor();
        int size = condition.size();

        boolean isBirthdaySort = "birthday".equals(sort);
        Limit limit = Limit.of(size + 1);
        FriendCursor birthdayCursor = isBirthdaySort && cursor != null ? FriendCursor.decode(cursor) : null;
        LocalDate referenceDate = isBirthdaySort
                ? birthdayCursor == null ? LocalDate.now(KOREA_ZONE) : birthdayCursor.referenceDate()
                : null;
        List<FriendSummaryResponse> friends = isBirthdaySort
                ? findAllByBirthday(userId, birthdayCursor, referenceDate, limit)
                : findAllByName(userId, cursor, limit);

        boolean hasNext = friends.size() > size;
        List<FriendSummaryResponse> pageItems = hasNext ? friends.subList(0, size) : friends;
        String nextCursor = hasNext
                ? isBirthdaySort
                        ? FriendCursor.from(referenceDate, pageItems.getLast()).encode()
                        : FriendNameCursor.from(pageItems.getLast()).encode()
                : null;
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
        if (keyword == null) {
            return null;
        }
        String stripped = keyword.strip();
        if (stripped.isEmpty() || stripped.length() > MAX_KEYWORD_LENGTH) {
            throw new BusinessException(CommonErrorCode.COMMON_INVALID_INPUT);
        }
        return stripped;
    }

    private List<FriendSummaryResponse> findAllByBirthday(
            Long userId, FriendCursor cursor, LocalDate referenceDate, Limit limit) {
        int currentBirthdayKey = FriendCursor.birthdayKey(referenceDate);
        if (cursor == null) {
            return friendRepository.findAllByOwnerUserIdOrderByUpcomingBirthday(userId, currentBirthdayKey, limit);
        }
        return friendRepository.findAllByOwnerUserIdAfterBirthdayCursor(
                userId, currentBirthdayKey, cursor.sortKey(), cursor.userId(), limit);
    }

    private List<FriendSummaryResponse> findAllByName(Long userId, String cursor, Limit limit) {
        if (cursor == null) {
            return friendRepository.findAllByOwnerUserIdOrderByName(userId, limit);
        }
        FriendNameCursor decodedCursor = FriendNameCursor.decode(cursor);
        return friendRepository.findAllByOwnerUserIdAfterNameCursor(
                userId, decodedCursor.name(), decodedCursor.userId(), limit);
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
