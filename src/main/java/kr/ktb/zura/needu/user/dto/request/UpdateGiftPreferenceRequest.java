package kr.ktb.zura.needu.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateGiftPreferenceRequest(
        @NotNull @Size(max = 5) List<@NotBlank String> interestCategoryCodes,
        @NotNull List<@NotBlank String> allergyCodes,
        @NotNull List<@NotBlank String> giftExclusionCodes
) {
}
