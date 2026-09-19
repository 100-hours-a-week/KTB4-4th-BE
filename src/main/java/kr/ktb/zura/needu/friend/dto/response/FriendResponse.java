package kr.ktb.zura.needu.friend.dto.response;

import kr.ktb.zura.needu.friend.entity.Friend;
import kr.ktb.zura.needu.user.entity.User;

public record FriendResponse(Long id, String nickname, String profileImageUrl, boolean tasteAnalysisCompleted) {

    public static FriendResponse from(Friend friend) {
        User friendUser = friend.getFriendUser();
        return new FriendResponse(
                friendUser.getId(),
                friendUser.getNickname(),
                friendUser.getProfileImageUrl(),
                friendUser.isTasteAnalysisCompleted()
        );
    }
}
