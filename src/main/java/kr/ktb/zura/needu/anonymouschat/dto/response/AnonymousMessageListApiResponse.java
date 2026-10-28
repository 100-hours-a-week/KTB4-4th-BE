package kr.ktb.zura.needu.anonymouschat.dto.response;

import java.util.List;

public record AnonymousMessageListApiResponse(
        String message,
        Data data,
        String nextCursor,
        boolean hasNext
) {

    public static AnonymousMessageListApiResponse of(String message, AnonymousMessageListResponse response) {
        return new AnonymousMessageListApiResponse(
                message,
                new Data(response.targetName(), response.page().items()),
                response.page().nextCursor(),
                response.page().hasNext()
        );
    }

    public record Data(String targetName, List<AnonymousMessageSummaryResponse> items) {
    }
}
