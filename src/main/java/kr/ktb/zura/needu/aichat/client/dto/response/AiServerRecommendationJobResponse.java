package kr.ktb.zura.needu.aichat.client.dto.response;

public record AiServerRecommendationJobResponse(
        AiServerRecommendationResult self,
        AiServerRecommendationResult gift
) {
}
