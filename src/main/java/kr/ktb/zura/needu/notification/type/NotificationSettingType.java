package kr.ktb.zura.needu.notification.type;

public enum NotificationSettingType {
    FRIEND_JOINED(true),
    FRIEND_BIRTHDAY(true),
    ANNIVERSARY_EVENT(true),
    MARKETING(false);

    private final boolean defaultEnabled;

    NotificationSettingType(boolean defaultEnabled) {
        this.defaultEnabled = defaultEnabled;
    }

    public boolean isDefaultEnabled() {
        return defaultEnabled;
    }
}
