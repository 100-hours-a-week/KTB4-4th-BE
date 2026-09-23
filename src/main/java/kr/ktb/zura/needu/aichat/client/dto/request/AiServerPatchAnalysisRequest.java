package kr.ktb.zura.needu.aichat.client.dto.request;

public record AiServerPatchAnalysisRequest(
        Long userId,
        String summary,
        AiServerAnalysisKeywordsRequest keywords
) {
}
