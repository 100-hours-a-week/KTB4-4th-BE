package kr.ktb.zura.needu.aichat.dto.response;

import kr.ktb.zura.needu.aichat.entity.AiMessage;

public record AiMessageResponse(
        Long userMessageId,
        Long messageId,
        String content,
        boolean inputLocked,
        AnalysisResultResponse analysis
) {

    public AiMessageResponse(Long userMessageId, Long messageId, String content) {
        this(userMessageId, messageId, content, false, null);
    }

    public static AiMessageResponse from(AiMessage reply) {
        return new AiMessageResponse(reply.getReplyToMessageId(), reply.getId(), reply.getContent());
    }

    public static AiMessageResponse from(
            AiMessage reply, boolean inputLocked, AnalysisResultResponse analysis) {
        return new AiMessageResponse(
                reply.getReplyToMessageId(), reply.getId(), reply.getContent(), inputLocked, analysis);
    }
}
