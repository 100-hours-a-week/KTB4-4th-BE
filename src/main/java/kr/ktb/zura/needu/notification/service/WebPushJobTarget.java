package kr.ktb.zura.needu.notification.service;

import java.util.List;
import kr.ktb.zura.needu.notification.type.NotificationType;

/**
 * 웹푸시 발송 작업을 만들 알림 이벤트 하나와 그 수신자의 알림 ID.
 */
public record WebPushJobTarget(
        Long notificationEventId,
        NotificationType type,
        List<Long> notificationIds
) {

    public WebPushJobTarget {
        notificationIds = List.copyOf(notificationIds);
    }
}
