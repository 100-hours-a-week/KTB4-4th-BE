package kr.ktb.zura.needu.product.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record UpdateProductFeedbackRequest(
        @NotNull @Pattern(regexp = "PERSONAL|MY_GIFT") String context,
        @Pattern(regexp = "LIKE|DISLIKE") String feedback
) {
}
