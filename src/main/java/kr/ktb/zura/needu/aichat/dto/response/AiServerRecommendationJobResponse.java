package kr.ktb.zura.needu.aichat.dto.response;

public record AiServerRecommendationJobResponse(
        AiServerRecommendationResult self,
        AiServerRecommendationResult gift
) {
}
