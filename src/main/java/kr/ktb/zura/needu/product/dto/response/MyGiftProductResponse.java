package kr.ktb.zura.needu.product.dto.response;

import java.math.BigDecimal;
import java.util.List;
import kr.ktb.zura.needu.product.repository.GiftProductSummary;
import kr.ktb.zura.needu.product.type.ProductCategory;
import kr.ktb.zura.needu.product.type.ProductFeedbackType;

public record MyGiftProductResponse(
        Long recommendationId,
        Long productId,
        String name,
        String productImageUrl,
        String purchaseUrl,
        ProductCategory category,
        Long price,
        BigDecimal score,
        List<String> matchingKeywords,
        String reason,
        ProductFeedbackType myFeedback
) {

    // TODO: myFeedback을 채울 것 (DISLIKE한 상품은 추천 행이 삭제되어 목록에 오지 않는다)
    public static MyGiftProductResponse from(GiftProductSummary giftProduct) {
        GiftProductResponse response = GiftProductResponse.from(giftProduct);
        return new MyGiftProductResponse(
                response.recommendationId(),
                response.productId(),
                response.name(),
                response.productImageUrl(),
                response.purchaseUrl(),
                response.category(),
                response.price(),
                response.score(),
                response.matchingKeywords(),
                response.reason(),
                null
        );
    }
}
