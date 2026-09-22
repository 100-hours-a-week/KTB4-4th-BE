package kr.ktb.zura.needu.aichat.client.dto.response;

public record AiServerStartSessionResponse(Long sessionId, String greeting, Integer maxTurns) {
}
