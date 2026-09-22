package kr.ktb.zura.needu.aichat.client.dto.response;

import java.util.List;

public record AiServerCloseSessionResponse(
        AiServerTasteProfile profile,
        String summary,
        AiServerAnalysisKeywordsResponse keywords,
        String profileCompleteness,
        List<String> missingSignals,
        String completionReason
) {
}
