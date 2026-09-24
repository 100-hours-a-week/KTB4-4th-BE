package kr.ktb.zura.needu.aichat.dto.response;

import kr.ktb.zura.needu.aichat.entity.AiMessage;

public record AiMessageResponse(
        Long userMessageId,
        Long messageId,
        String content,
        Integer progress,
        Boolean inputLocked
) {

    public static AiMessageResponse from(AiMessage reply) {
        return new AiMessageResponse(
                reply.getReplyToMessageId(), reply.getId(), reply.getContent(),
                reply.getProgress(), reply.getInputLocked());
    }
}
