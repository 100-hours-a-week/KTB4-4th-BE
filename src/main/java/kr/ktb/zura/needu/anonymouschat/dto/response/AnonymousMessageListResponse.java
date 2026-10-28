package kr.ktb.zura.needu.anonymouschat.dto.response;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import kr.ktb.zura.needu.anonymouschat.entity.AnonymousRoom;
import kr.ktb.zura.needu.anonymouschat.type.AnonymousRoomEndedReason;
import kr.ktb.zura.needu.anonymouschat.type.AnonymousRoomStatus;
import kr.ktb.zura.needu.common.response.CursorPageResponse;

public record AnonymousMessageListResponse(
        String targetName,
        AnonymousRoomStatus roomStatus,
        AnonymousRoomEndedReason endedReason,
        OffsetDateTime endedAt,
        CursorPageResponse<AnonymousMessageSummaryResponse> page
) {

    public static AnonymousMessageListResponse of(
            AnonymousRoom room,
            Long viewerUserId,
            String targetName,
            CursorPageResponse<AnonymousMessageSummaryResponse> page
    ) {
        if (!room.isEnded()) {
            return new AnonymousMessageListResponse(targetName, room.getStatus(), null, null, page);
        }
        return new AnonymousMessageListResponse(
                targetName,
                room.getStatus(),
                AnonymousRoomEndedReason.of(room.getEndedByUserId(), viewerUserId),
                room.getEndedAt().atOffset(ZoneOffset.UTC),
                page
        );
    }
}
