package kr.ktb.zura.needu.friend.dto.response;

public record FriendBirthdayNotificationTarget(
        Long ownerUserId,
        Long friendUserId,
        String nickname
) {
}
