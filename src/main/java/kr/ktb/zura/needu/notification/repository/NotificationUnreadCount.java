package kr.ktb.zura.needu.notification.repository;

public interface NotificationUnreadCount {

    Long getReceiverUserId();

    long getUnreadCount();
}
