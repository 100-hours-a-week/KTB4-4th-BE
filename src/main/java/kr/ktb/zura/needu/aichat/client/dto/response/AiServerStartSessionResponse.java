package kr.ktb.zura.needu.aichat.client.dto.response;

import java.time.OffsetDateTime;

public record AiServerStartSessionResponse(
        Long conversationRoomId,
        String greeting,
        OffsetDateTime createdAt,
        OffsetDateTime expirationAt,
        Integer maxTurns
) {
}
