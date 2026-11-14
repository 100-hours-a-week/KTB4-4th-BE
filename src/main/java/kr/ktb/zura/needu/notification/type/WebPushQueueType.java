package kr.ktb.zura.needu.notification.type;

// 웹푸시 작업을 처리하는 대기열. 대기열마다 워커를 따로 두어 대량 발송이 밀려도 실시간 알림이 기다리지 않게 한다
public enum WebPushQueueType {
    // 사용자 행동에 바로 반응해야 하는 알림
    REALTIME,
    // 배치·스케줄러가 한꺼번에 만드는 알림
    BULK
}
