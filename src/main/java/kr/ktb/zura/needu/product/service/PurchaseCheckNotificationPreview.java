package kr.ktb.zura.needu.product.service;

import kr.ktb.zura.needu.notification.service.NotificationPreview;
import kr.ktb.zura.needu.product.entity.ProductLinkClick;
import kr.ktb.zura.needu.product.type.ProductContext;

// friendUserId와 friendName은 친구 관계가 확인될 때만 함께 존재
public record PurchaseCheckNotificationPreview(
        Long productId,
        String productName,
        String productImageUrl,
        ProductContext context,
        Long friendUserId,
        String friendName
) implements NotificationPreview {

    static PurchaseCheckNotificationPreview of(ProductLinkClick click, String friendName) {
        return new PurchaseCheckNotificationPreview(
                click.getProduct().getId(),
                click.getProduct().getName(),
                click.getProduct().getImageUrl(),
                click.getContext(),
                friendName == null ? null : click.getFriendUserId(),
                friendName
        );
    }
}
