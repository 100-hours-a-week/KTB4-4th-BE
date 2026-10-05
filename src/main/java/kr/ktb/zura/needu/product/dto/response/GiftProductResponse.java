package kr.ktb.zura.needu.product.dto.response;

import java.math.BigDecimal;
import java.util.List;

import kr.ktb.zura.needu.product.repository.GiftProductSummary;

public record GiftProductResponse(
        Long recommendationId,
        Long productId,
        String productImageUrl,
        String purchaseUrl,
        String category,
        String name,
        Long price,
        BigDecimal score,
        List<String> matchingKeywords,
        String reason
) {

    public static GiftProductResponse from(GiftProductSummary giftProduct) {
        return new GiftProductResponse(
                giftProduct.id(),
                giftProduct.productId(),
                giftProduct.productImageUrl(),
                giftProduct.purchaseUrl(),
                giftProduct.category(),
                giftProduct.productName(),
                toPrice(giftProduct.price()),
                giftProduct.score(),
                toMatchingKeywords(giftProduct.tasteKeywords()),
                giftProduct.reason()
        );
    }

    // 원화 가격만 다루므로 DB의 소수 자릿수(scale 2)를 버리고 정수로 내려준다.
    private static Long toPrice(BigDecimal price) {
        return price == null ? null : price.longValue();
    }

    private static List<String> toMatchingKeywords(List<String> tasteKeywords) {
        return tasteKeywords == null ? List.of() : tasteKeywords;
    }
}
