package kr.ktb.zura.needu.aichat.client.dto.request;

public record AiServerSendMessageStreamRequest(Long userId, Long userMessageId, String message) {
}
