package kr.ktb.zura.needu.aichat.client.dto.response;

public record AiServerCloseSessionResponse(
        Long conversationId,
        Long userId,
        String summary,
        AiServerCloseSessionKeywordsResponse keywords,
        AiServerRecommendationsResponse recommendations
) {
}
