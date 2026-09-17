package kr.ktb.zura.needu.product.dto.response;

import java.math.BigDecimal;

import kr.ktb.zura.needu.product.entity.PersonalProduct;
import kr.ktb.zura.needu.product.entity.Product;

public record PersonalProductResponse(Long recommendationId, Long productId, String name, String imageUrl, Long price) {

    public static PersonalProductResponse from(PersonalProduct personalProduct) {
        Product product = personalProduct.getProduct();
        return new PersonalProductResponse(
                personalProduct.getId(),
                product.getId(),
                product.getName(),
                product.getImageUrl(),
                toPrice(product.getPrice())
        );
    }

    // 원화 가격만 다루므로 DB의 소수 자릿수(scale 2)를 버리고 정수로 내려준다.
    private static Long toPrice(BigDecimal price) {
        return price == null ? null : price.longValue();
    }
}
