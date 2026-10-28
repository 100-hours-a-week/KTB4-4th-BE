package kr.ktb.zura.needu.anonymouschat.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import kr.ktb.zura.needu.anonymouschat.type.AnonymousRoomEndedReason;
import kr.ktb.zura.needu.anonymouschat.type.AnonymousRoomStatus;

public record AnonymousMessageListApiResponse(
        String message,
        Data data,
        String nextCursor,
        boolean hasNext
) {

    public static AnonymousMessageListApiResponse of(String message, AnonymousMessageListResponse response) {
        return new AnonymousMessageListApiResponse(
                message,
                new Data(
                        response.targetName(),
                        response.roomStatus(),
                        response.endedReason(),
                        response.endedAt(),
                        response.page().items()
                ),
                response.page().nextCursor(),
                response.page().hasNext()
        );
    }

    public record Data(
            String targetName,
            AnonymousRoomStatus roomStatus,
            AnonymousRoomEndedReason endedReason,
            OffsetDateTime endedAt,
            List<AnonymousMessageSummaryResponse> items
    ) {
    }
}
