package kr.ktb.zura.needu.aichat.client.dto.response;

import java.util.List;

public record AiServerAnalysisResponse(
        AiServerTasteProfile profile,
        String summary,
        AiServerAnalysisKeywordsResponse keywords,
        Boolean correctionAvailable,
        String profileCompleteness,
        List<String> missingSignals
) {
}
