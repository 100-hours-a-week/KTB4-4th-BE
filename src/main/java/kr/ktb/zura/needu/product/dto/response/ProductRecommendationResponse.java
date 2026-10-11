package kr.ktb.zura.needu.product.dto.response;

import java.util.List;

import kr.ktb.zura.needu.product.entity.GiftProduct;
import kr.ktb.zura.needu.product.entity.PersonalProduct;

public record ProductRecommendationResponse(Long recommendationId, String reason, List<String> matchingKeywords) {

    public static ProductRecommendationResponse from(PersonalProduct personalProduct) {
        return new ProductRecommendationResponse(personalProduct.getId(), personalProduct.getReason(), List.of());
    }

    public static ProductRecommendationResponse from(GiftProduct giftProduct) {
        List<String> tasteKeywords = giftProduct.getTasteKeywords();
        return new ProductRecommendationResponse(
                giftProduct.getId(), giftProduct.getReason(), tasteKeywords == null ? List.of() : tasteKeywords);
    }
}
