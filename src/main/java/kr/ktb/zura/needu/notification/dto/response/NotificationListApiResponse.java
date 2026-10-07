package kr.ktb.zura.needu.notification.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record NotificationListApiResponse(
        String message,
        Data data,
        String nextCursor,
        boolean hasNext
) {

    public static NotificationListApiResponse of(String message, NotificationListResponse response) {
        return new NotificationListApiResponse(
                message,
                new Data(response.items(), response.unreadCount(), response.newCount(), response.serverTime()),
                response.nextCursor(),
                response.hasNext()
        );
    }

    public record Data(
            List<NotificationResponse> items,
            long unreadCount,
            long newCount,
            LocalDateTime serverTime
    ) {
    }
}
