package kr.ktb.zura.needu.friend.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import kr.ktb.zura.needu.friend.dto.response.FriendJoinedNotificationTarget;
import kr.ktb.zura.needu.friend.repository.FriendRepository;
import kr.ktb.zura.needu.notification.service.NotificationCreationService;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// 친구 관계 저장 트랜잭션과 분리해 호출한다. 알림 실패가 이미 저장된 친구 관계를 되돌리지 않도록
// 트랜잭션을 두지 않고, 대상별로 NotificationCreationService의 개별 트랜잭션에서 생성한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class FriendJoinedNotificationService {

    private static final String BODY = "새로운 친구의 취향을 확인해 보세요.";
    private static final long NOTIFICATION_EXPIRATION_DAYS = 30;

    private final FriendRepository friendRepository;
    private final NotificationCreationService notificationCreationService;

    public void createNotifications(Long receiverUserId, Collection<Long> actorUserIds) {
        if (actorUserIds.isEmpty()) {
            return;
        }

        List<FriendJoinedNotificationTarget> targets;
        try {
            targets = friendRepository.findAllFriendJoinedNotificationTargets(receiverUserId, actorUserIds);
        } catch (RuntimeException exception) {
            log.warn("친구 가입 알림 대상 조회 실패. userId={}", receiverUserId, exception);
            return;
        }

        LocalDateTime expiresAt = LocalDateTime.now(ZoneOffset.UTC).plusDays(NOTIFICATION_EXPIRATION_DAYS);
        targets.forEach(target -> createNotification(receiverUserId, target, expiresAt));
    }

    private void createNotification(
            Long receiverUserId,
            FriendJoinedNotificationTarget target,
            LocalDateTime expiresAt
    ) {
        try {
            notificationCreationService.createNotifications(
                    Set.of(receiverUserId),
                    NotificationType.FRIEND_JOINED,
                    target.actorUserId(),
                    target.nickname() + "님이 NEEDU에 가입했어요!",
                    BODY,
                    NotificationResourceType.USER,
                    target.actorUserId(),
                    "friend-joined:" + receiverUserId + ":" + target.actorUserId(),
                    expiresAt
            );
        } catch (RuntimeException exception) {
            log.warn("친구 가입 알림 생성 실패. userId={}, actorUserId={}",
                    receiverUserId, target.actorUserId(), exception);
        }
    }
}
