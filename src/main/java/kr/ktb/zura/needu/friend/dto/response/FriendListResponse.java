package kr.ktb.zura.needu.friend.dto.response;

import java.util.List;
import kr.ktb.zura.needu.common.response.CursorPageResponse;

public record FriendListResponse(
        List<FriendSummaryResponse> friends,
        boolean hasNext,
        String nextCursor
) {

    public static FriendListResponse from(CursorPageResponse<FriendSummaryResponse> page) {
        return new FriendListResponse(page.items(), page.hasNext(), page.nextCursor());
    }
}
