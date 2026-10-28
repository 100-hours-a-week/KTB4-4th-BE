package kr.ktb.zura.needu.anonymouschat.dto.response;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import kr.ktb.zura.needu.anonymouschat.entity.AnonymousRoom;

public record AnonymousRoomCreatedResponse(Long roomId, OffsetDateTime createdAt) {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    public static AnonymousRoomCreatedResponse from(AnonymousRoom room) {
        return new AnonymousRoomCreatedResponse(
                room.getId(),
                room.getCreatedAt().atOffset(ZoneOffset.UTC)
                        .atZoneSameInstant(SEOUL_ZONE)
                        .toOffsetDateTime()
        );
    }
}
