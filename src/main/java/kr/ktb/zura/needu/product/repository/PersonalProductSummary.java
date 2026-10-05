package kr.ktb.zura.needu.product.repository;

import java.math.BigDecimal;

public record PersonalProductSummary(
        Long id,
        Long productId,
        String productName,
        String productImageUrl,
        String purchaseUrl,
        String category,
        BigDecimal price,
        BigDecimal score,
        String reason
) {
}
