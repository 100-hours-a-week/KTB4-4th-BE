package kr.ktb.zura.needu.friend.dto.response;

import java.util.List;

public record FriendListApiResponse(
        String message,
        Data data,
        String nextCursor,
        boolean hasNext
) {

    public static FriendListApiResponse of(String message, FriendListResponse response) {
        return new FriendListApiResponse(
                message,
                new Data(response.items(), response.isKakaoFriendSynced()),
                response.nextCursor(),
                response.hasNext()
        );
    }

    public record Data(List<FriendSummaryResponse> items, boolean isKakaoFriendSynced) {
    }
}
