package kr.ktb.zura.needu.product.dto.response;

import java.math.BigDecimal;
import java.util.List;

import kr.ktb.zura.needu.product.entity.GiftProduct;
import kr.ktb.zura.needu.product.entity.Product;

public record GiftProductResponse(
        Long recommendationId,
        Long productId,
        String productImageUrl,
        String category,
        String name,
        Long price,
        List<String> matchingKeywords,
        String reason
) {

    public static GiftProductResponse from(GiftProduct giftProduct) {
        Product product = giftProduct.getProduct();
        return new GiftProductResponse(
                giftProduct.getId(),
                product.getId(),
                product.getImageUrl(),
                product.getCategory(),
                product.getName(),
                toPrice(product.getPrice()),
                toMatchingKeywords(giftProduct.getTasteKeywords()),
                giftProduct.getReason()
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
