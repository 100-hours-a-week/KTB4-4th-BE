package kr.ktb.zura.needu.aichat.facade;

import kr.ktb.zura.needu.aichat.dto.response.AiConversationResponse;

public record AiConversationStartResult(AiConversationResponse conversation, boolean isCreated) {

    public static AiConversationStartResult created(AiConversationResponse conversation) {
        return new AiConversationStartResult(conversation, true);
    }

    public static AiConversationStartResult resumed(AiConversationResponse conversation) {
        return new AiConversationStartResult(conversation, false);
    }
}
