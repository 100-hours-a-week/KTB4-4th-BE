package kr.ktb.zura.needu.friend.dto.response;

public record UpcomingFriendResponse(
        Long userId,
        String nickname,
        String profileImageUrl
) {

    public static UpcomingFriendResponse from(UpcomingFriendBirthdayTarget target) {
        return new UpcomingFriendResponse(target.userId(), target.nickname(), target.profileImageUrl());
    }
}
