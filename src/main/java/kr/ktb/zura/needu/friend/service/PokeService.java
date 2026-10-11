package kr.ktb.zura.needu.friend.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.friend.dto.response.PokeResponse;
import kr.ktb.zura.needu.friend.exception.FriendErrorCode;
import kr.ktb.zura.needu.friend.repository.FriendRepository;
import kr.ktb.zura.needu.notification.service.NotificationCreationService;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationType;
import kr.ktb.zura.needu.user.dto.response.UserSummaryResponse;
import kr.ktb.zura.needu.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PokeService {

    private static final long NOTIFICATION_EXPIRATION_DAYS = 30;

    private final UserService userService;
    private final FriendRepository friendRepository;
    private final NotificationCreationService notificationCreationService;

    @Transactional
    public PokeResponse createPoke(Long userId, Long friendUserId) {
        UserSummaryResponse user = userService.findUserSummary(userId);
        if (!friendRepository.existsByOwnerUserIdAndFriendUserId(userId, friendUserId)) {
            throw new BusinessException(FriendErrorCode.FRIEND_NOT_FOUND);
        }

        LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);
        notificationCreationService.createNotifications(
                Set.of(friendUserId),
                NotificationType.POKE,
                userId,
                user.nickname() + "님이 나를 콕 찔렀어요!",
                "AI대화를 통해 취향 분석을 해보세요!",
                NotificationResourceType.AI_CONVERSATION,
                null,
                UUID.randomUUID().toString(),
                createdAt.plusDays(NOTIFICATION_EXPIRATION_DAYS)
        );
        return new PokeResponse(createdAt);
    }
}
