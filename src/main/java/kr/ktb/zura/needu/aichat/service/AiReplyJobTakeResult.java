package kr.ktb.zura.needu.aichat.service;

import kr.ktb.zura.needu.aichat.entity.AiMessage;
import kr.ktb.zura.needu.aichat.entity.AiReplyJob;

public record AiReplyJobTakeResult(
        Outcome outcome,
        Long jobId,
        Long conversationId,
        Long userId,
        Long userMessageId,
        String lockToken,
        int attempt,
        AiMessage userMessage
) {

    public enum Outcome {
        // 작업이 없거나, 다시 접수돼 배달 토큰이 바뀌었거나, 이미 실패로 닫힘
        STALE,
        // 앞선 처리가 답변을 저장함 => 마무리 이벤트만 다시 보낸다
        ALREADY_SUCCEEDED,
        // 작업 할당 횟수 한도를 넘겨 실패로 닫음
        GAVE_UP,
        TAKEN
    }

    static AiReplyJobTakeResult stale(Long jobId) {
        return new AiReplyJobTakeResult(Outcome.STALE, jobId, null, null, null, null, 0, null);
    }

    static AiReplyJobTakeResult alreadySucceeded(AiReplyJob job) {
        return of(Outcome.ALREADY_SUCCEEDED, job, null);
    }

    static AiReplyJobTakeResult gaveUp(AiReplyJob job) {
        return of(Outcome.GAVE_UP, job, null);
    }

    static AiReplyJobTakeResult taken(AiReplyJob job, AiMessage userMessage) {
        return of(Outcome.TAKEN, job, userMessage);
    }

    private static AiReplyJobTakeResult of(Outcome outcome, AiReplyJob job, AiMessage userMessage) {
        return new AiReplyJobTakeResult(outcome, job.getId(), job.getAiChatRoom().getId(), job.getUserId(),
                job.getUserMessageId(), job.getLockToken(), job.getAttempt(), userMessage);
    }
}
