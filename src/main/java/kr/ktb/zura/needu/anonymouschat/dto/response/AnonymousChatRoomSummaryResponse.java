package kr.ktb.zura.needu.anonymouschat.dto.response;

import java.time.OffsetDateTime;

public record AnonymousChatRoomSummaryResponse(
        Long roomId,
        String counterpartAlias,
        boolean hasUnreadMessage,
        long unreadMessageCount,
        OffsetDateTime lastActiveAt,
        AnonymousChatLastMessageResponse lastMessage,
        AnonymousChatProductResponse product
) {
}
