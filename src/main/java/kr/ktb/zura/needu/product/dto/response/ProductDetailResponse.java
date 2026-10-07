package kr.ktb.zura.needu.product.dto.response;

import kr.ktb.zura.needu.product.type.ProductFeedbackType;

// myFeedback은 내 추천 목록(PERSONAL, MY_GIFT)에서만 값이 존재, FRIEND_GIFT는 항상 null
public record ProductDetailResponse(
        Long productId,
        String name,
        String category,
        String description,
        Long price,
        String productImageUrl,
        String purchaseUrl,
        ProductRecommendationResponse recommendation,
        ProductFeedbackType myFeedback
) {
}
