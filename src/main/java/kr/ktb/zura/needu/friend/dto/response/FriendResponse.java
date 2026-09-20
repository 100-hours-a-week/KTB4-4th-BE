package kr.ktb.zura.needu.friend.dto.response;

import kr.ktb.zura.needu.user.dto.response.UserResponse;
import kr.ktb.zura.needu.user.dto.response.UserSummaryResponse;

public record FriendResponse(Long id, String nickname, String profileImageUrl, boolean tasteAnalysisCompleted) {

    public static FriendResponse from(UserResponse friendUser, UserSummaryResponse friendSummary) {
        return new FriendResponse(
                friendUser.id(),
                friendUser.nickname(),
                friendUser.profileImageUrl(),
                friendSummary.tasteAnalysisCompleted()
        );
    }
}
