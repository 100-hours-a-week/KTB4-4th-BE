package kr.ktb.zura.needu.product.dto.response;

import java.math.BigDecimal;

import kr.ktb.zura.needu.product.entity.Product;
import kr.ktb.zura.needu.product.type.ProductCategory;
import kr.ktb.zura.needu.product.type.ProductFeedbackType;

// myFeedback은 내 추천 목록(PERSONAL, MY_GIFT)에서만 값이 존재, FRIEND_GIFT는 항상 null
public record ProductDetailResponse(
        Long productId,
        String name,
        ProductCategory category,
        String description,
        Long price,
        String productImageUrl,
        String purchaseUrl,
        ProductRecommendationResponse recommendation,
        ProductFeedbackType myFeedback
) {

    public static ProductDetailResponse from(
            Product product, ProductRecommendationResponse recommendation, ProductFeedbackType myFeedback) {
        return new ProductDetailResponse(
                product.getId(),
                product.getName(),
                product.getCategory(),
                product.getDescription(),
                toPrice(product.getPrice()),
                product.getImageUrl(),
                product.getPurchaseUrl(),
                recommendation,
                myFeedback
        );
    }

    private static Long toPrice(BigDecimal price) {
        return price == null ? null : price.longValue();
    }
}
