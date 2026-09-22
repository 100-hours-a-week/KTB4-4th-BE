package kr.ktb.zura.needu.aichat.dto.response;

public record AnalysisResultResponse(
        String summary,
        AnalysisKeywordsResponse keywords,
        boolean correctionAvailable
) {

    public static AnalysisResultResponse from(AiServerAnalysisResponse response) {
        return new AnalysisResultResponse(
                response.summary(),
                AnalysisKeywordsResponse.from(response.keywords()),
                response.correctionAvailable()
        );
    }
}
