package kr.ktb.zura.needu.anonymouschat.ws;

import tools.jackson.databind.JsonNode;

public record AnonymousMessageRedisEvent(
        Long receiverUserId,
        JsonNode event
) {
}
