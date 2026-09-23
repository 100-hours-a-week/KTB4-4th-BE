package kr.ktb.zura.needu.aichat.dto.response;

import java.time.Instant;
import java.util.List;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerSessionResponse;

public record AiSessionResponse(
        Long sessionId,
        Long userId,
        String status,
        Integer turn,
        Integer maxTurns,
        List<AiSessionMessageResponse> messages,
        Boolean inputLocked,
        Boolean canClose,
        Instant lastActiveAt
) {

    public static AiSessionResponse from(AiServerSessionResponse response) {
        return new AiSessionResponse(
                response.sessionId(),
                response.userId(),
                response.status(),
                response.turn(),
                response.maxTurns(),
                response.messages().stream().map(AiSessionMessageResponse::from).toList(),
                response.inputLocked(),
                response.canClose(),
                response.lastActiveAt()
        );
    }
}
