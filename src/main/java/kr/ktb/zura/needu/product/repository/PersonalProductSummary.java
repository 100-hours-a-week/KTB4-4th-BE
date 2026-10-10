package kr.ktb.zura.needu.product.repository;

import java.math.BigDecimal;
import kr.ktb.zura.needu.product.type.ProductCategory;

public record PersonalProductSummary(
        Long id,
        Long productId,
        String productName,
        String productImageUrl,
        String purchaseUrl,
        ProductCategory category,
        BigDecimal price,
        BigDecimal score,
        String reason
) {
}
