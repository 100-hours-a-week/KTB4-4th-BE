package kr.ktb.zura.needu.friend.dto.response;

public record FriendSummaryResponse(
        Long userId,
        String nickname,
        String profileImageUrl,
        boolean favorite
) {
}
