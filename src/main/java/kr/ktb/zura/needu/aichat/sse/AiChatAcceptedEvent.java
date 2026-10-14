package kr.ktb.zura.needu.aichat.sse;

import java.util.UUID;

public record AiChatAcceptedEvent(Long userMessageId, UUID clientMessageId) {
}
