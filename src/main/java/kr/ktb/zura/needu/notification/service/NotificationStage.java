package kr.ktb.zura.needu.notification.service;

// 알림 생성부터 SSE 발행까지 시간을 나눠 재는 단계
public enum NotificationStage {

    DEDUP_CHECK("dedup_check"),
    SETTING_LOOKUP("setting_lookup"),
    EVENT_INSERT("event_insert"),
    NOTIFICATION_INSERT("notification_insert"),
    PRESENCE_LOOKUP("presence_lookup"),
    UNREAD_LOOKUP("unread_lookup"),
    PREVIEW_LOOKUP("preview_lookup"),
    PUBLISH("publish");

    private final String metricTag;

    NotificationStage(String metricTag) {
        this.metricTag = metricTag;
    }

    public String metricTag() {
        return metricTag;
    }
}
