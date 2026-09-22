package kr.ktb.zura.needu.aichat.dto.response;

public record AiServerMatchedSignal(
        String field,
        String value,
        String label,
        String tasteNodeId,
        String via,
        Double similarity,
        Double contribution
) {
}
