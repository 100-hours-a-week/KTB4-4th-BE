package kr.ktb.zura.needu.product.repository;

import java.math.BigDecimal;

public record ProductPriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
}
