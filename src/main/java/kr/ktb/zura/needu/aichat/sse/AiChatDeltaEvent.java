package kr.ktb.zura.needu.aichat.sse;

public record AiChatDeltaEvent(Long userMessageId, int index, String text) {
}
