package kr.ktb.zura.needu.product.dto.request;

import jakarta.validation.constraints.NotNull;

public record SubmitPurchaseCheckRequest(@NotNull Boolean purchased) {
}
