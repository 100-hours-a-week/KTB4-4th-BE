package kr.ktb.zura.needu.aichat.dto.response;

import java.util.List;

public record AnalysisKeywordsResponse(List<String> taste, List<String> interest) {

    public static AnalysisKeywordsResponse from(AiServerAnalysisKeywordsResponse response) {
        return new AnalysisKeywordsResponse(response.taste(), response.interest());
    }
}
