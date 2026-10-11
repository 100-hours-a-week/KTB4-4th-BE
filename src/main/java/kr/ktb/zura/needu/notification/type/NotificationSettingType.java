package kr.ktb.zura.needu.notification.type;

public enum NotificationSettingType {
    CHAT(true),
    POKE(true),
    FRIEND_JOINED(true),
    FRIEND_BIRTHDAY(true),
    PURCHASE_STATUS(true),
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
