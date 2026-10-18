package kr.ktb.zura.needu.product.dto.response;

import java.math.BigDecimal;

import kr.ktb.zura.needu.product.entity.Product;
import kr.ktb.zura.needu.product.type.ProductStatus;

public record PurchaseCheckProductResponse(
        Long productId,
        String name,
        String productImageUrl,
        String purchaseUrl,
        Long price,
        ProductStatus status
) {

    public static PurchaseCheckProductResponse from(Product product) {
        return new PurchaseCheckProductResponse(
                product.getId(),
                product.getName(),
                product.getImageUrl(),
                product.getPurchaseUrl(),
                toPrice(product.getPrice()),
                product.getStatus()
        );
    }

    private static Long toPrice(BigDecimal price) {
        return price == null ? null : price.longValue();
    }
}
