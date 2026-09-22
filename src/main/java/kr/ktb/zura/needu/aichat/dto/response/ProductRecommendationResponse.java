package kr.ktb.zura.needu.aichat.dto.response;

import kr.ktb.zura.needu.aichat.client.dto.response.AiServerRecommendationJobResponse;

public record ProductRecommendationResponse() {

    public static ProductRecommendationResponse from(AiServerRecommendationJobResponse response) {
        return new ProductRecommendationResponse();
    }
}
