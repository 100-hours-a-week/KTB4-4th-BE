package kr.ktb.zura.needu.friend.dto.response;

import java.util.List;
import kr.ktb.zura.needu.common.response.CursorPageResponse;

public record FriendListResponse(
        List<FriendSummaryResponse> items,
        boolean isKakaoFriendSynced,
        long totalCount,
        long favoriteTotalCount,
        String nextCursor,
        boolean hasNext
) {

    public static FriendListResponse from(
            CursorPageResponse<FriendSummaryResponse> page,
            boolean isKakaoFriendSynced,
            long totalCount,
            long favoriteTotalCount
    ) {
        return new FriendListResponse(
                page.items(), isKakaoFriendSynced, totalCount, favoriteTotalCount,
                page.nextCursor(), page.hasNext());
    }
}
