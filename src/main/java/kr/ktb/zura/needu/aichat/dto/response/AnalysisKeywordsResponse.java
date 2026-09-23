package kr.ktb.zura.needu.aichat.dto.response;

import java.util.List;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerAnalysisKeywordsResponse;

public record AnalysisKeywordsResponse(
        List<AnalysisKeywordResponse> taste,
        List<AnalysisKeywordResponse> interest
) {

    public static AnalysisKeywordsResponse from(AiServerAnalysisKeywordsResponse response) {
        return new AnalysisKeywordsResponse(
                response.taste().stream().map(AnalysisKeywordResponse::from).toList(),
                response.interest().stream().map(AnalysisKeywordResponse::from).toList()
        );
    }
}
