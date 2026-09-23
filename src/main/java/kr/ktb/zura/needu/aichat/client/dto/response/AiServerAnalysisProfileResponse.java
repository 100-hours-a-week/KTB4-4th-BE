package kr.ktb.zura.needu.aichat.client.dto.response;

public record AiServerAnalysisProfileResponse(
        Long userId,
        String summary,
        AiServerAnalysisKeywordsResponse keywords,
        Boolean correctionAvailable
) {
}
