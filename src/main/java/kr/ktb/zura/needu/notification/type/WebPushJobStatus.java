package kr.ktb.zura.needu.notification.type;

// 성공한 작업은 행을 바로 삭제하므로 성공 상태는 두지 않는다
public enum WebPushJobStatus {
    QUEUED,
    RUNNING,
    FAILED
}
