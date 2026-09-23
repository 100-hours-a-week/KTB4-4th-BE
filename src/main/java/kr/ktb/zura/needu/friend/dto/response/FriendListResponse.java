package kr.ktb.zura.needu.friend.dto.response;

import java.util.List;
import kr.ktb.zura.needu.common.response.CursorPageResponse;

public record FriendListResponse(
        List<FriendSummaryResponse> items,
        boolean isKakaoFriendSynced,
        String nextCursor,
        boolean hasNext
) {

    public static FriendListResponse from(
            CursorPageResponse<FriendSummaryResponse> page,
            boolean isKakaoFriendSynced
    ) {
        return new FriendListResponse(
                page.items(), isKakaoFriendSynced, page.nextCursor(), page.hasNext());
    }
}
