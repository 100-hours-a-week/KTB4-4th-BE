package kr.ktb.zura.needu.aichat.dto.response;

public record AiServerStartSessionResponse(Long sessionId, String greeting, Integer maxTurns) {
}
