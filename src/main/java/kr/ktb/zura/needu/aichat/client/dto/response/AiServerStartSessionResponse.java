package kr.ktb.zura.needu.aichat.client.dto.response;

import java.time.LocalDateTime;

public record AiServerStartSessionResponse(
        Long sessionId,
        String greeting,
        LocalDateTime createdAt,
        Integer maxTurns
) {
}
