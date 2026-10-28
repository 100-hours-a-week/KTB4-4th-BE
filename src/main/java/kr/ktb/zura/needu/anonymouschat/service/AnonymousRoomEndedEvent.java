package kr.ktb.zura.needu.anonymouschat.service;

import java.time.OffsetDateTime;

public record AnonymousRoomEndedEvent(
        Long receiverUserId,
        Long roomId,
        OffsetDateTime endedAt
) {
}
