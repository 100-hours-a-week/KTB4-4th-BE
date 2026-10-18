package kr.ktb.zura.needu.notification.dto.response;

import java.time.LocalDateTime;

import kr.ktb.zura.needu.common.response.CursorPageResponse;

public record NotificationListResponse(
        CursorPageResponse<NotificationResponse> page,
        long unreadCount,
        long newCount,
        LocalDateTime serverTime
) {
}
