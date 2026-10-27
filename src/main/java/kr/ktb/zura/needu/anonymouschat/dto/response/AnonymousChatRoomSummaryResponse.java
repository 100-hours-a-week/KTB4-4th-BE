package kr.ktb.zura.needu.anonymouschat.dto.response;

import java.time.LocalDateTime;

public record AnonymousChatRoomSummaryResponse(
        Long roomId,
        String counterpartAlias,
        boolean hasUnreadMessage,
        long unreadMessageCount,
        LocalDateTime lastActiveAt,
        AnonymousChatLastMessageResponse lastMessage,
        AnonymousChatProductResponse product
) {
}
