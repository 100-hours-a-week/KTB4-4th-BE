package kr.ktb.zura.needu.guidance.dto.response;

import kr.ktb.zura.needu.guidance.service.candidate.GuidanceCandidate;

public record GuidanceContentResponse(String title, String description) {

    public static GuidanceContentResponse from(GuidanceCandidate candidate) {
        return new GuidanceContentResponse(candidate.title(), candidate.description());
    }
}
