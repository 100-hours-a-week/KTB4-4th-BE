package kr.ktb.zura.needu.aichat.repository;

public enum AiReplyHeartbeatResult {
    OWNED,
    CLAIMED_BY_OTHER,
    // 이미 ACK됐거나 Redis가 잃음
    NOT_PENDING
}
