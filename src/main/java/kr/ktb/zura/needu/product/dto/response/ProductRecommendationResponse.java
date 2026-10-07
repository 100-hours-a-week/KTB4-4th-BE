package kr.ktb.zura.needu.product.dto.response;

import java.util.List;

public record ProductRecommendationResponse(Long recommendationId, String reason, List<String> matchingKeywords) {
}
