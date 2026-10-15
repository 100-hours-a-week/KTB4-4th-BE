package kr.ktb.zura.needu.product.dto.response;

import java.time.LocalDateTime;

import kr.ktb.zura.needu.product.entity.ProductLinkClick;
import kr.ktb.zura.needu.product.entity.PurchaseCheck;
import kr.ktb.zura.needu.product.type.ProductContext;

// friend는 FRIEND_GIFT이면서 지금도 활성 상태인 친구일 때만 존재
public record PurchaseCheckDetailResponse(
        Long purchaseCheckId,
        ProductContext context,
        PurchaseCheckFriendResponse friend,
        PurchaseCheckProductResponse product,
        boolean answered,
        Boolean purchased,
        LocalDateTime answeredAt,
        LocalDateTime clickedAt
) {

    public static PurchaseCheckDetailResponse from(PurchaseCheck purchaseCheck, PurchaseCheckFriendResponse friend) {
        ProductLinkClick click = purchaseCheck.getProductLinkClick();
        return new PurchaseCheckDetailResponse(
                purchaseCheck.getId(),
                click.getContext(),
                friend,
                PurchaseCheckProductResponse.from(click.getProduct()),
                purchaseCheck.isAnswered(),
                purchaseCheck.getPurchased(),
                purchaseCheck.getAnsweredAt(),
                click.getCreatedAt()
        );
    }
}
