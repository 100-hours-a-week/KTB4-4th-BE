package kr.ktb.zura.needu.aichat.dto.response;

import java.util.List;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerAnalysisKeywordsResponse;

public record AnalysisKeywordsResponse(List<String> taste, List<String> interest) {

    public static AnalysisKeywordsResponse from(AiServerAnalysisKeywordsResponse response) {
        return new AnalysisKeywordsResponse(response.taste(), response.interest());
    }
}
