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
        Integer itemCount,
        Boolean inputLocked,
        Boolean analysisAvailable,
        Boolean canClose,
        String completionReason,
        String profileCompleteness,
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
                response.itemCount(),
                response.inputLocked(),
                response.analysisAvailable(),
                response.canClose(),
                response.completionReason(),
                response.profileCompleteness(),
                response.lastActiveAt()
        );
    }
}
