package kr.ktb.zura.needu.product.dto.response;

import java.math.BigDecimal;

import kr.ktb.zura.needu.product.repository.ProductPriceRange;

public record PriceRangeResponse(Long minPrice, Long maxPrice) {

    public static PriceRangeResponse from(ProductPriceRange priceRange) {
        return new PriceRangeResponse(toPrice(priceRange.minPrice()), toPrice(priceRange.maxPrice()));
    }

    private static Long toPrice(BigDecimal price) {
        return price == null ? null : price.longValue();
    }
}
