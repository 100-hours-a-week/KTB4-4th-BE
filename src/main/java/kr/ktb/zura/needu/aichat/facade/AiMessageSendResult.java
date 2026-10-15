package kr.ktb.zura.needu.aichat.facade;

import kr.ktb.zura.needu.aichat.dto.response.AiMessageAcceptedResponse;
import kr.ktb.zura.needu.aichat.dto.response.AiMessageResponse;

public record AiMessageSendResult(AiMessageAcceptedResponse accepted, AiMessageResponse reply) {

    public static AiMessageSendResult accepted(AiMessageAcceptedResponse accepted) {
        return new AiMessageSendResult(accepted, null);
    }

    public static AiMessageSendResult replied(AiMessageResponse reply) {
        return new AiMessageSendResult(null, reply);
    }

    public boolean isAccepted() {
        return accepted != null;
    }
}
