package kr.ktb.zura.needu.aichat.client.dto.request;

import kr.ktb.zura.needu.aichat.client.dto.response.AiServerTasteProfile;

public record AiServerRecommendationJobRequest(
        Long userId,
        Long sessionId,
        AiServerTasteProfile profile
) {
}
