package kr.ktb.zura.needu.aichat.dto.response;

import kr.ktb.zura.needu.aichat.entity.AiMessage;

public record AiMessageResponse(Long userMessageId, Long messageId, String content) {

    public static AiMessageResponse from(AiMessage reply) {
        return new AiMessageResponse(reply.getReplyToMessageId(), reply.getId(), reply.getContent());
    }
}
