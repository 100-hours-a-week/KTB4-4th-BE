package kr.ktb.zura.needu.aichat.client.dto.response;

import java.time.Instant;
import java.util.List;

public record AiServerRecommendationResult(
        String recommendationId,
        Instant generatedAt,
        String mode,
        AiServerPriceRange priceRange,
        List<AiServerRecommendedItem> items,
        String emptyReason,
        String suggestion,
        AiServerRecommendationFunnel funnel
) {
}
