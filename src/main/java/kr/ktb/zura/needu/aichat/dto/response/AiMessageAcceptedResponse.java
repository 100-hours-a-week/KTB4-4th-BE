package kr.ktb.zura.needu.aichat.dto.response;

import java.util.UUID;

import kr.ktb.zura.needu.aichat.entity.AiMessage;

public record AiMessageAcceptedResponse(Long userMessageId, UUID clientMessageId) {

    public static AiMessageAcceptedResponse from(AiMessage userMessage) {
        return new AiMessageAcceptedResponse(userMessage.getId(), userMessage.getClientMessageId());
    }
}
