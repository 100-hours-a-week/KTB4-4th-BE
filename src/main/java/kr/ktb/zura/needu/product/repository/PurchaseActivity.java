package kr.ktb.zura.needu.product.repository;

import java.time.LocalDateTime;

import kr.ktb.zura.needu.product.type.PlatformType;
import kr.ktb.zura.needu.product.type.ProductContext;

public record PurchaseActivity(
        PlatformType platformType,
        String externalId,
        ProductContext context,
        LocalDateTime occurredAt
) {
}
