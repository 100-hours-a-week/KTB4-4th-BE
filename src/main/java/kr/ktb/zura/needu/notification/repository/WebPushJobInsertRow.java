package kr.ktb.zura.needu.notification.repository;

import java.util.List;
import kr.ktb.zura.needu.notification.type.WebPushQueueType;

public record WebPushJobInsertRow(
        Long notificationEventId,
        WebPushQueueType queueType,
        List<Long> notificationIds
) {

    public WebPushJobInsertRow {
        if (queueType == null) {
            throw new IllegalArgumentException("Web push job must have a queue type.");
        }
        if (notificationIds == null || notificationIds.isEmpty()) {
            throw new IllegalArgumentException("Web push job must have at least one notification id.");
        }
        notificationIds = List.copyOf(notificationIds);
    }
}
