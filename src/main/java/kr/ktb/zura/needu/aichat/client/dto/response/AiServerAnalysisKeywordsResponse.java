package kr.ktb.zura.needu.aichat.client.dto.response;

import java.util.List;

public record AiServerAnalysisKeywordsResponse(
        List<AiServerAnalysisKeywordResponse> taste,
        List<AiServerAnalysisKeywordResponse> interest
) {
}
