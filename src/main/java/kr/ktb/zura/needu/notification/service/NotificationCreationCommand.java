package kr.ktb.zura.needu.notification.service;

import java.time.LocalDateTime;
import java.util.Set;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationType;

/**
 * 알림 이벤트 하나와 그 수신자들을 만들 때 필요한 값
 *
 * @param actorUserId 시스템이 발생시킨 알림이면 {@code null}
 * @param resourceType 연결할 대상 리소스가 없으면 {@code null}
 * @param resourceId 연결할 대상 리소스가 없으면 {@code null}
 * @param dedupKey 중복 생성을 막을 필요가 없는 이벤트면 {@code null}
 */
public record NotificationCreationCommand(
        Set<Long> receiverUserIds,
        NotificationType type,
        Long actorUserId,
        String title,
        String body,
        NotificationResourceType resourceType,
        Long resourceId,
        String dedupKey,
        LocalDateTime expiresAt
) {
}
