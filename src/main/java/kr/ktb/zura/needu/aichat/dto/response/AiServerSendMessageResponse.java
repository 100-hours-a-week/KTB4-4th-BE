package kr.ktb.zura.needu.aichat.dto.response;

// AI 명세 3번 응답 중 V1에서 쓰는 필드만 매핑한다(turn, inputLocked 등 대화 종료 판단용 필드는 V2에서 사용).
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
