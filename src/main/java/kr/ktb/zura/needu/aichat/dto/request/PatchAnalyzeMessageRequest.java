package kr.ktb.zura.needu.aichat.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PatchAnalyzeMessageRequest(
        @NotBlank String summary,
        @Valid @NotNull AnalysisKeywordsRequest keywords
) {
}
