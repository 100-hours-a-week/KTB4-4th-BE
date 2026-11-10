package kr.ktb.zura.needu.anonymouschat.dto.response;

import java.time.OffsetDateTime;
import kr.ktb.zura.needu.anonymouschat.entity.AnonymousRoom;
import kr.ktb.zura.needu.anonymouschat.support.AnonymousChatTime;

public record AnonymousRoomCreatedResponse(Long roomId, OffsetDateTime createdAt) {

    public static AnonymousRoomCreatedResponse from(AnonymousRoom room) {
        return new AnonymousRoomCreatedResponse(
                room.getId(),
                AnonymousChatTime.toKst(room.getCreatedAt())
        );
    }
}
