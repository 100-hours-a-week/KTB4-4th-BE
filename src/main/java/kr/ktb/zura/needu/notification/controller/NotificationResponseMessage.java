package kr.ktb.zura.needu.notification.controller;

enum NotificationResponseMessage {

    NOTIFICATION_SUMMARY_FOUND("미읽음 알림 상태를 조회했습니다."),
    NOTIFICATIONS_FOUND("알림을 조회했습니다."),
    NOTIFICATION_READ("알림을 읽음 처리했습니다."),
    NOTIFICATION_SETTING_FOUND("알림 설정을 조회했습니다."),
    NOTIFICATION_SETTING_UPDATED("알림 설정을 저장했습니다."),
    PUSH_SUBSCRIBED("웹 푸시 알림을 켰습니다.");

    private final String message;

    NotificationResponseMessage(String message) {
        this.message = message;
    }

    String getMessage() {
        return message;
    }
}
