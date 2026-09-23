package kr.ktb.zura.needu.aichat.dto.response;

public record ProductRecommendationStatusResponse(
        boolean isRecommendationCompleted
) {

    public static ProductRecommendationStatusResponse success() {
        return new ProductRecommendationStatusResponse(true);
    }
}
