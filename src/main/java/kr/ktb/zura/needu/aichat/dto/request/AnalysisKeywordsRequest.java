package kr.ktb.zura.needu.aichat.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AnalysisKeywordsRequest(
        @NotNull @Size(max = 3) List<@NotBlank String> taste,
        @NotNull @Size(max = 3) List<@NotBlank String> interest
) {
}
