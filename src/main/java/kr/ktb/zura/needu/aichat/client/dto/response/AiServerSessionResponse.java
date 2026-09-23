package kr.ktb.zura.needu.aichat.client.dto.response;

import java.time.Instant;
import java.util.List;

public record AiServerSessionResponse(
        Long sessionId,
        Long userId,
        String status,
        Integer turn,
        Integer maxTurns,
        List<AiServerSessionMessageResponse> messages,
        Boolean inputLocked,
        Boolean canClose,
        Instant lastActiveAt
) {
}
