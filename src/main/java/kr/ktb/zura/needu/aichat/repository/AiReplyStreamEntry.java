package kr.ktb.zura.needu.aichat.repository;

public record AiReplyStreamEntry(
        String entryId,
        Long jobId,
        String dispatchToken,
        long deliveryCount
) {

    public boolean isMalformed() {
        return jobId == null || dispatchToken == null;
    }
}
