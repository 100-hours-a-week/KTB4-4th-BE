package kr.ktb.zura.needu.notification.sse;

import tools.jackson.databind.JsonNode;

// Redis Pub/Sub으로 인스턴스 간 전달하는 알림 이벤트 형식이다.
public record NotificationRedisEvent(
        Long receiverUserId,
        JsonNode data,
        long unreadCount
) {
}
