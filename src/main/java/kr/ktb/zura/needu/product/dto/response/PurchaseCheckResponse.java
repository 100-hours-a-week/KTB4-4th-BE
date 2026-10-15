package kr.ktb.zura.needu.product.dto.response;

import java.time.LocalDateTime;

import kr.ktb.zura.needu.product.entity.PurchaseCheck;

public record PurchaseCheckResponse(Long purchaseCheckId, boolean purchased, LocalDateTime answeredAt) {

    public static PurchaseCheckResponse from(PurchaseCheck purchaseCheck) {
        return new PurchaseCheckResponse(
                purchaseCheck.getId(), purchaseCheck.getPurchased(), purchaseCheck.getAnsweredAt());
    }
}
