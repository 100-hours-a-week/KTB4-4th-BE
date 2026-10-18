package kr.ktb.zura.needu.product.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ProductDetailRequest(
        @NotNull
        @Pattern(regexp = "PERSONAL|MY_GIFT|FRIEND_GIFT")
        String context,
        Long userId
) {
}
