package kr.ktb.zura.needu.friend.dto.response;

import java.util.List;

public record FriendOverviewResponse(
        List<FriendSummaryResponse> items,
        boolean kakaoFriendSynced
) {
}
