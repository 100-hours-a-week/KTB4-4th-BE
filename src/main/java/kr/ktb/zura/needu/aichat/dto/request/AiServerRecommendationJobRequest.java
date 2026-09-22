package kr.ktb.zura.needu.aichat.dto.request;

import kr.ktb.zura.needu.aichat.dto.response.AiServerTasteProfile;

public record AiServerRecommendationJobRequest(
        Long userId,
        Long sessionId,
        AiServerTasteProfile profile
) {
}
