package kr.ktb.zura.needu.aichat.service;

import kr.ktb.zura.needu.aichat.entity.AiMessage;

public record AiReplyCommitResult(AiMessage reply) {

    public static AiReplyCommitResult saved(AiMessage reply) {
        return new AiReplyCommitResult(reply);
    }

    static AiReplyCommitResult rejected() {
        return new AiReplyCommitResult(null);
    }

    public boolean isRejected() {
        return reply == null;
    }
}
