package kr.ktb.zura.needu.anonymouschat.service;

import kr.ktb.zura.needu.notification.service.NotificationPreview;
import kr.ktb.zura.needu.product.dto.response.ProductSummaryResponse;

public record AnonymousRoomNotificationPreview(
        Long productId,
        String productName,
        String productImageUrl
) implements NotificationPreview {

    static AnonymousRoomNotificationPreview from(ProductSummaryResponse product) {
        return new AnonymousRoomNotificationPreview(
                product.productId(), product.name(), product.imageUrl());
    }
}
