package kr.ktb.zura.needu.aichat.client.dto.response;

public record AiServerRecommendationsResponse(
        AiServerRecommendationResult self,
        AiServerRecommendationResult gift
) {
}
