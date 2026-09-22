package kr.ktb.zura.needu.aichat.client.dto.response;

public record AiServerSendMessageResponse(
        String reply,
        Integer turn,
        Integer maxTurns,
        Boolean canClose,
        Integer itemCount,
        Boolean inputLocked,
        String completionReason,
        String profileCompleteness,
        Boolean lastTurnExtractionFailed
) {
}
