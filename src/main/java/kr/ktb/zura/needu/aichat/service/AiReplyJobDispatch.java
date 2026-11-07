package kr.ktb.zura.needu.aichat.service;

import kr.ktb.zura.needu.aichat.entity.AiMessage;
import kr.ktb.zura.needu.aichat.entity.AiReplyJob;

public record AiReplyJobDispatch(Long jobId, String dispatchToken, AiMessage userMessage) {

    static AiReplyJobDispatch of(AiReplyJob job, AiMessage userMessage) {
        return new AiReplyJobDispatch(job.getId(), job.getDispatchToken(), userMessage);
    }
}
