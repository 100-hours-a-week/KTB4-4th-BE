package kr.ktb.zura.needu.anonymouschat.dto.response;

import kr.ktb.zura.needu.common.response.CursorPageResponse;

public record AnonymousMessageListResponse(
        String targetName,
        CursorPageResponse<AnonymousMessageSummaryResponse> page
) {
}
