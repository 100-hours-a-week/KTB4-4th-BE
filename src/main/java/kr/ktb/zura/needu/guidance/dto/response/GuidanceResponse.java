package kr.ktb.zura.needu.guidance.dto.response;

import kr.ktb.zura.needu.guidance.service.candidate.GuidanceCandidate;

public record GuidanceResponse(boolean tasteAnalysisCompleted, GuidanceContentResponse guidance) {

    public static GuidanceResponse from(boolean tasteAnalysisCompleted, GuidanceCandidate candidate) {
        return new GuidanceResponse(tasteAnalysisCompleted, GuidanceContentResponse.from(candidate));
    }
}
