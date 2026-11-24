package kr.ktb.zura.needu.anonymouschat.ws;

public record AnonymousTypingEvent(
        Long roomId,
        boolean isTyping
) {
}
