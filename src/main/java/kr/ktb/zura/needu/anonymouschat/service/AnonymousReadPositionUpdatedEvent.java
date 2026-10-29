package kr.ktb.zura.needu.anonymouschat.service;

import java.time.LocalDateTime;

public record AnonymousReadPositionUpdatedEvent(
        Long roomId,
        Long receiverUserId,
        Long lastReadMessageId,
        LocalDateTime readAt
) {
}
