package kr.ktb.zura.needu.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import kr.ktb.zura.needu.user.type.Gender;

public record CompleteOnboardingRequest(
        @NotNull Gender gender,
        @NotNull @Past LocalDate birthDate,
        @Size(max = 5) List<@NotBlank String> interestCategoryCodes,
        List<@NotBlank String> allergyCodes,
        List<@NotBlank String> giftExclusionCodes
) {
}
