package kr.ktb.zura.needu.product.repository;

import java.math.BigDecimal;
import java.util.List;

public record GiftProductSummary(
        Long id,
        Long productId,
        String productName,
        String productImageUrl,
        String purchaseUrl,
        String category,
        BigDecimal price,
        BigDecimal score,
        String reason,
        List<String> tasteKeywords
) {
}