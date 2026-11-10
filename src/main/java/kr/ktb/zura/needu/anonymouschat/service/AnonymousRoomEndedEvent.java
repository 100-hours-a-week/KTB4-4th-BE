package kr.ktb.zura.needu.anonymouschat.service;

import java.time.OffsetDateTime;
import kr.ktb.zura.needu.anonymouschat.type.AnonymousRoomEndedReason;
import kr.ktb.zura.needu.anonymouschat.type.AnonymousRoomStatus;

public record AnonymousRoomEndedEvent(
        Long receiverUserId,
        Long roomId,
        AnonymousRoomStatus roomStatus,
        AnonymousRoomEndedReason endedReason,
        OffsetDateTime endedAt
) {
}
