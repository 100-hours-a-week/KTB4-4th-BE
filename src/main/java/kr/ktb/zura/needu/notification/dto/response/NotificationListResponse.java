package kr.ktb.zura.needu.notification.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record NotificationListResponse(
        List<NotificationResponse> items,
        long unreadCount,
        long newCount,
        LocalDateTime serverTime,
        String nextCursor,
        boolean hasNext
) {
}
