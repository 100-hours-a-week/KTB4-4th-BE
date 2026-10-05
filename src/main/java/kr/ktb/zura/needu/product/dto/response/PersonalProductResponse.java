package kr.ktb.zura.needu.product.dto.response;

import java.math.BigDecimal;

import kr.ktb.zura.needu.product.repository.PersonalProductSummary;

public record PersonalProductResponse(
        Long recommendationId,
        Long productId,
        String name,
        String productImageUrl,
        String purchaseUrl,
        String category,
        Long price,
        BigDecimal score,
        String reason) {

    public static PersonalProductResponse from(PersonalProductSummary personalProduct) {
        return new PersonalProductResponse(
                personalProduct.id(),
                personalProduct.productId(),
                personalProduct.productName(),
                personalProduct.productImageUrl(),
                personalProduct.purchaseUrl(),
                personalProduct.category(),
                toPrice(personalProduct.price()),
                personalProduct.score(),
                personalProduct.reason()
        );
    }

    // 원화 가격만 다루므로 DB의 소수 자릿수(scale 2)를 버리고 정수로 내려준다.
    private static Long toPrice(BigDecimal price) {
        return price == null ? null : price.longValue();
    }
}
