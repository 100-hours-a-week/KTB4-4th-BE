package kr.ktb.zura.needu.aichat.client.dto.response;

import java.time.LocalDateTime;

public record AiServerSendMessageResponse(
        String reply,
        LocalDateTime createdAt,
        Integer turn,
        Integer maxTurns,
        Boolean canClose,
        Boolean inputLocked,
        Integer progress
) {
}
