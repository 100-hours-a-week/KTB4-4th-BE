package kr.ktb.zura.needu.friend.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.response.RetryAfterResponse;
import kr.ktb.zura.needu.friend.dto.response.PokeResponse;
import kr.ktb.zura.needu.friend.exception.FriendErrorCode;
import kr.ktb.zura.needu.friend.repository.FriendRepository;
import kr.ktb.zura.needu.friend.repository.PokeCooldownRepository;
import kr.ktb.zura.needu.notification.service.NotificationCreationService;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationType;
import kr.ktb.zura.needu.user.dto.response.UserSummaryResponse;
import kr.ktb.zura.needu.user.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PokeService {

    private static final long NOTIFICATION_EXPIRATION_DAYS = 30;

    private final UserService userService;
    private final FriendRepository friendRepository;
    private final PokeCooldownRepository pokeCooldownRepository;
    private final NotificationCreationService notificationCreationService;
    private final Duration pokeCooldown;

    public PokeService(
            UserService userService,
            FriendRepository friendRepository,
            PokeCooldownRepository pokeCooldownRepository,
            NotificationCreationService notificationCreationService,
            @Value("${needu.friend.poke-cooldown}") Duration pokeCooldown
    ) {
        this.userService = userService;
        this.friendRepository = friendRepository;
        this.pokeCooldownRepository = pokeCooldownRepository;
        this.notificationCreationService = notificationCreationService;
        this.pokeCooldown = pokeCooldown;
    }

    public PokeResponse createPoke(Long userId, Long friendUserId) {
        UserSummaryResponse user = userService.findUserSummary(userId);
        if (!friendRepository.existsByOwnerUserIdAndFriendUserId(userId, friendUserId)) {
            throw new BusinessException(FriendErrorCode.FRIEND_NOT_FOUND);
        }

        String requestId = UUID.randomUUID().toString();
        if (!pokeCooldownRepository.create(userId, friendUserId, requestId, pokeCooldown)) {
            throw new BusinessException(
                    FriendErrorCode.FRIEND_POKE_TOO_FREQUENT,
                    new RetryAfterResponse(pokeCooldownRepository.findRemainingSeconds(userId, friendUserId))
            );
        }

        LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);
        try {
            notificationCreationService.createNotifications(
                    Set.of(friendUserId),
                    NotificationType.POKE,
                    userId,
                    user.nickname() + "님이 나를 콕 찔렀어요!",
                    "AI대화를 통해 취향 분석을 해보세요!",
                    NotificationResourceType.AI_CONVERSATION,
                    null,
                    requestId,
                    createdAt.plusDays(NOTIFICATION_EXPIRATION_DAYS)
            );
        } catch (RuntimeException exception) {
            try {
                pokeCooldownRepository.delete(userId, friendUserId, requestId);
            } catch (RuntimeException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
        return new PokeResponse(friendUserId, createdAt, createdAt.plus(pokeCooldown));
    }
}
