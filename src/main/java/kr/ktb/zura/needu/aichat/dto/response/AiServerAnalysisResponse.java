package kr.ktb.zura.needu.aichat.dto.response;

public record AiServerAnalysisResponse(
        String summary,
        AiServerAnalysisKeywordsResponse keywords,
        Boolean correctionAvailable
) {
}
