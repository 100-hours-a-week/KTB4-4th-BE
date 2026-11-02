package kr.ktb.zura.needu.notification.sse;

import java.util.List;
import tools.jackson.databind.JsonNode;

// Redis Pub/Sub으로 인스턴스 간 전달하는 알림 이벤트 형식이다.
// 같은 알림을 받는 사용자들을 한 메시지로 묶고, 공통 값은 data에 한 번만 담는다.
public record NotificationRedisEvent(
        JsonNode data,
        List<Recipient> recipients
) {

    // data에는 notificationId처럼 받는 사용자마다 다른 값만 담는다.
    public record Recipient(
            Long receiverUserId,
            JsonNode data,
            long unreadCount
    ) {
    }
}
