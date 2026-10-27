package kr.ktb.zura.needu.anonymouschat.dto.response;

import java.time.OffsetDateTime;

public record AnonymousChatLastMessageResponse(
        Long messageId,
        String content,
        String sender,
        OffsetDateTime sentAt
) {
}
