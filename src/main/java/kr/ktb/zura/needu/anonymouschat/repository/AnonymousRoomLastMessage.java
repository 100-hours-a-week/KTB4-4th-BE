package kr.ktb.zura.needu.anonymouschat.repository;

import java.time.LocalDateTime;

public record AnonymousRoomLastMessage(
        Long roomId,
        Long messageId,
        String content,
        Long senderUserId,
        LocalDateTime sentAt
) {
}
