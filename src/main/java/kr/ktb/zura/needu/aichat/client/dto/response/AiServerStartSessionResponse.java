package kr.ktb.zura.needu.aichat.client.dto.response;

import java.time.OffsetDateTime;

public record AiServerStartSessionResponse(
        Long sessionId,
        String greeting,
        OffsetDateTime createdAt,
        Integer maxTurns
) {
}
