package kr.ktb.zura.needu.friend.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
import kr.ktb.zura.needu.friend.dto.response.FriendListResponse;
import kr.ktb.zura.needu.friend.dto.response.FriendSummaryResponse;
import kr.ktb.zura.needu.friend.entity.Friend;
import kr.ktb.zura.needu.friend.exception.FriendErrorCode;
import kr.ktb.zura.needu.friend.repository.FriendRepository;
import kr.ktb.zura.needu.friend.repository.FriendSyncRequestRepository;
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

    // MySQL 문자열 리터럴에서 역슬래시가 이스케이프로 해석되지 않도록 LIKE 이스케이프 문자로 '!'를 쓴다
    private static final String LIKE_ESCAPE = "!";
    private static final String LIKE_ANY = "%";

    private final FriendRepository friendRepository;
    private final FriendSyncRequestRepository friendSyncRequestRepository;
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

    public Map<Long, String> findAllFriendNicknames(Long ownerUserId, Collection<Long> friendUserIds) {
        if (friendUserIds.isEmpty()) {
            return Map.of();
        }
        List<Long> confirmedFriendUserIds = friendRepository
                .findAllByOwnerUserIdAndFriendUserIdIn(ownerUserId, friendUserIds).stream()
                .map(Friend::getFriendUserId)
                .toList();
        if (confirmedFriendUserIds.isEmpty()) {
            return Map.of();
        }
        return userService.findAllActiveUsersByIds(confirmedFriendUserIds).stream()
                .collect(Collectors.toMap(UserDetailResponse::id, UserDetailResponse::nickname));
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
        userService.validateActiveUser(userId);
        boolean isKakaoFriendSynced = userService.isKakaoFriendSynced(userId);

        String cursor = condition.cursor();
        int size = condition.size();
        boolean isFavoriteOnly = Boolean.TRUE.equals(condition.favorite());
        String namePrefixPattern = toNamePrefixPattern(condition.keyword());
        List<FriendSummaryResponse> friends = findAllByName(
                userId, isFavoriteOnly, namePrefixPattern, cursor, Limit.of(size + 1));

        boolean hasNext = friends.size() > size;
        List<FriendSummaryResponse> pageItems = hasNext ? friends.subList(0, size) : friends;
        String nextCursor = hasNext ? FriendNameCursor.from(pageItems.getLast()).encode() : null;
        long totalCount = friendRepository.countAllActiveByOwnerUserId(userId);
        long favoriteTotalCount = friendRepository.countAllActiveFavoritesByOwnerUserId(userId);
        return FriendListResponse.from(
                new CursorPageResponse<>(pageItems, nextCursor, hasNext),
                isKakaoFriendSynced,
                totalCount,
                favoriteTotalCount);
    }

    private Friend findFriendRelation(Long ownerUserId, Long friendUserId) {
        return friendRepository.findByOwnerUserIdAndFriendUserId(ownerUserId, friendUserId)
                .orElseThrow(() -> new BusinessException(FriendErrorCode.FRIEND_NOT_FOUND));
    }

    private UserDetailResponse findActiveFriendUser(Long friendUserId) {
        return userService.findUserById(friendUserId)
                .orElseThrow(() -> new BusinessException(FriendErrorCode.FRIEND_NOT_FOUND));
    }

    private String toNamePrefixPattern(String keyword) {
        if (keyword == null) {
            return LIKE_ANY;
        }
        String escapedKeyword = keyword.strip()
                .replace(LIKE_ESCAPE, LIKE_ESCAPE + LIKE_ESCAPE)
                .replace("%", LIKE_ESCAPE + "%")
                .replace("_", LIKE_ESCAPE + "_");
        return escapedKeyword + LIKE_ANY;
    }

    private List<FriendSummaryResponse> findAllByName(
            Long userId, boolean isFavoriteOnly, String namePrefixPattern, String cursor, Limit limit) {
        if (cursor == null) {
            return friendRepository.findAllByOwnerUserIdOrderByName(userId, isFavoriteOnly, namePrefixPattern, limit);
        }
        FriendNameCursor decodedCursor = FriendNameCursor.decode(cursor);
        return friendRepository.findAllByOwnerUserIdAfterNameCursor(
                userId, isFavoriteOnly, namePrefixPattern, decodedCursor.name(), decodedCursor.userId(), limit);
    }

    @Transactional
    public void syncKakaoFriends(Long ownerUserId, Collection<Long> kakaoFriendIds) {
        Map<Long, Long> userIdsByKakaoId = getUserIdsByKakaoId(kakaoFriendIds);

        List<Friend> newFriends = saveNewKakaoFriends(ownerUserId, userIdsByKakaoId);
        LocalDateTime requestedAt = LocalDateTime.now(ZoneOffset.UTC);
        newFriends.forEach(friend -> friendSyncRequestRepository.createIfAbsent(
                friend.getFriendUserId(), requestedAt));
        userService.completeKakaoFriendSync(ownerUserId);
    }


    @Transactional
    public List<Long> syncRequestedKakaoFriends(Long ownerUserId, Collection<Long> kakaoFriendIds) {
        Map<Long, Long> userIdsByKakaoId = getUserIdsByKakaoId(kakaoFriendIds);
        List<Friend> newFriends = saveNewKakaoFriends(ownerUserId, userIdsByKakaoId);
        userService.completeKakaoFriendSync(ownerUserId);
        return newFriends.stream()
                .map(Friend::getFriendUserId)
                .toList();
    }

    private Map<Long, Long> getUserIdsByKakaoId(Collection<Long> kakaoFriendIds) {
        return userService.findUserIdsByExternalIds(Set.copyOf(kakaoFriendIds));
    }

    private List<Friend> saveNewKakaoFriends(Long ownerUserId, Map<Long, Long> userIdsByKakaoId) {
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

        return newFriends;
    }
}
