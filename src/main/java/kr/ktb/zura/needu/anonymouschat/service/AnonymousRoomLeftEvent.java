package kr.ktb.zura.needu.anonymouschat.service;

import java.time.OffsetDateTime;
import kr.ktb.zura.needu.anonymouschat.type.AnonymousRoomStatus;

public record AnonymousRoomLeftEvent(
        Long receiverUserId,
        Long roomId,
        AnonymousRoomStatus roomStatus,
        OffsetDateTime leftAt
) {
}
