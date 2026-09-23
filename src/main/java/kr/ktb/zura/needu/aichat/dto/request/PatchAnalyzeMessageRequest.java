package kr.ktb.zura.needu.aichat.dto.request;

import jakarta.validation.constraints.NotNull;

public record PatchAnalyzeMessageRequest(
        String summary,
        @NotNull AnalysisKeywordsRequest keywords
) {
}
