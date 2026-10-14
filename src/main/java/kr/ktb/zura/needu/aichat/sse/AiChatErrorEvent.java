package kr.ktb.zura.needu.aichat.sse;

public record AiChatErrorEvent(
        Long userMessageId,
        String message,
        boolean retryable,
        boolean restartRequired
) {
}
