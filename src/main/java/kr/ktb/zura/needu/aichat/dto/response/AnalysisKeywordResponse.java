package kr.ktb.zura.needu.aichat.dto.response;

import kr.ktb.zura.needu.aichat.client.dto.response.AiServerAnalysisKeywordResponse;

public record AnalysisKeywordResponse(String value, Double score) {

    public static AnalysisKeywordResponse from(AiServerAnalysisKeywordResponse response) {
        return new AnalysisKeywordResponse(response.value(), response.score());
    }
}
