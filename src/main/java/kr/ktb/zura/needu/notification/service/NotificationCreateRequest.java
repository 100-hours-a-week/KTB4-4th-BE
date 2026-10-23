package kr.ktb.zura.needu.notification.service;

import java.time.LocalDateTime;
import java.util.Set;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;

/**
 * 묶음 생성에서 알림 이벤트 하나를 만드는 요청. 각 값의 의미는 단건 생성과 같다.
 *
 * @param actorUserId 시스템이 발생시킨 알림이면 {@code null}
 * @param resourceType 연결할 대상 리소스가 없으면 {@code null}
 * @param resourceId 연결할 대상 리소스가 없으면 {@code null}
 * @param dedupKey 중복 생성을 막을 필요가 없는 이벤트면 {@code null}
 */
public record NotificationCreateRequest(
        Set<Long> receiverUserIds,
        Long actorUserId,
        String title,
        String body,
        NotificationResourceType resourceType,
        Long resourceId,
        String dedupKey,
        LocalDateTime expiresAt
) {
}
