package kr.ktb.zura.needu.aichat.dto.response;

public record AiServerRecommendationFunnel(
        Integer retrieved,
        Integer afterHardFilter,
        Integer afterScoreFloor,
        Integer returned
) {
}
