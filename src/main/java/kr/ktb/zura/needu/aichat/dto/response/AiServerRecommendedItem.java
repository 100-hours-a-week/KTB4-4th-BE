package kr.ktb.zura.needu.aichat.dto.response;

import java.util.List;

public record AiServerRecommendedItem(
        Long productId,
        String platform,
        String title,
        Integer price,
        String imageUrl,
        String productUrl,
        Integer rank,
        Double score,
        String reason,
        String category,
        Integer priceBand,
        List<AiServerMatchedSignal> matchedSignals,
        AiServerRanking ranking
) {
}
