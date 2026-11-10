package kr.ktb.zura.needu.aichat.client.dto.response;

import java.util.Map;

public record AiServerCloseSessionResponse(
        Long conversationId,
        Long userId,
        String summary,
        AiServerAnalysisKeywordsResponse keywords,
        AiServerRecommendationsResponse recommendations,
        Map<String, Object> memory
) {
}
