package kr.ktb.zura.needu.product.dto.response;

import java.time.LocalDateTime;

public record PurchaseCheckResponse(Long purchaseCheckId, boolean purchased, LocalDateTime answeredAt) {
}
