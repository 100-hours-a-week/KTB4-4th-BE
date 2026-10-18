package kr.ktb.zura.needu.aichat.sse;

import tools.jackson.databind.JsonNode;

// Redis Pub/Sub으로 인스턴스 간 전달하는 AI 채팅 이벤트 형식이다.
public record AiChatRedisEvent(
        Long conversationId,
        AiChatEventType type,
        JsonNode data
) {
}
