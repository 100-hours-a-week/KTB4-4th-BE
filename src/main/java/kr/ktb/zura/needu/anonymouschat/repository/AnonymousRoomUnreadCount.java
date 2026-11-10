package kr.ktb.zura.needu.anonymouschat.repository;

public record AnonymousRoomUnreadCount(
        Long roomId,
        Long unreadMessageCount
) {
}
