package kr.ktb.zura.needu.product.dto.response;

import java.math.BigDecimal;
import kr.ktb.zura.needu.product.entity.Product;
import kr.ktb.zura.needu.product.type.ProductCategory;

public record ProductSummaryResponse(
        Long productId,
        String name,
        Long price,
        ProductCategory category,
        String imageUrl
) {

    public static ProductSummaryResponse from(Product product) {
        return new ProductSummaryResponse(
                product.getId(),
                product.getName(),
                toPrice(product.getPrice()),
                product.getCategory(),
                product.getImageUrl()
        );
    }

    private static Long toPrice(BigDecimal price) {
        return price == null ? null : price.longValue();
    }
}
