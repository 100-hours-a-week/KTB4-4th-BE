package kr.ktb.zura.needu.product.dto.response;

import java.math.BigDecimal;

import kr.ktb.zura.needu.product.repository.PersonalProductSummary;
import kr.ktb.zura.needu.product.type.ProductCategory;
import kr.ktb.zura.needu.product.type.ProductFeedbackType;

public record PersonalProductResponse(
        Long recommendationId,
        Long productId,
        String name,
        String productImageUrl,
        String purchaseUrl,
        ProductCategory category,
        Long price,
        BigDecimal score,
        String reason,
        ProductFeedbackType myFeedback) {

    // TODO: 만족도 테이블을 추가한 뒤 myFeedback을 채우고 DISLIKE 상품은 목록에서 뺄 것
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
                personalProduct.reason(),
                null
        );
    }

    // 원화 가격만 다루므로 DB의 소수 자릿수(scale 2)를 버리고 정수로 내려준다.
    private static Long toPrice(BigDecimal price) {
        return price == null ? null : price.longValue();
    }
}
