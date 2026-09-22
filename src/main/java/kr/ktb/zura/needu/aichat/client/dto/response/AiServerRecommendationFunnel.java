package kr.ktb.zura.needu.aichat.client.dto.response;

public record AiServerRecommendationFunnel(
        Integer retrieved,
        Integer afterHardFilter,
        Integer afterScoreFloor,
        Integer returned
) {
}
