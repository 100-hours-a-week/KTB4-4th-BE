package kr.ktb.zura.needu.product.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateProductLinkClickRequest(
        @NotNull Long productId,
        @NotNull @Pattern(regexp = "PERSONAL|MY_GIFT|FRIEND_GIFT") String context,
        Long friendUserId,
        Long recommendationId
) {

    private static final String FRIEND_GIFT = "FRIEND_GIFT";

    @AssertTrue
    public boolean isFriendUserIdPresentForFriendGift() {
        return !FRIEND_GIFT.equals(context) || friendUserId != null;
    }
}
