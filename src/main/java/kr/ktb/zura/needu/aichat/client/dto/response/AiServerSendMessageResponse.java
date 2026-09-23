package kr.ktb.zura.needu.aichat.client.dto.response;

import java.time.OffsetDateTime;

public record AiServerSendMessageResponse(
        String reply,
        OffsetDateTime createdAt,
        Integer turn,
        Integer maxTurns,
        Boolean canClose,
        Boolean inputLocked,
        Integer progress
) {
}
