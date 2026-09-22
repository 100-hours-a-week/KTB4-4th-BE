package kr.ktb.zura.needu.guidance.service.candidate;

import kr.ktb.zura.needu.guidance.type.GuidanceContent;

public record GuidanceCandidate(String title, String description) {

    public static GuidanceCandidate from(GuidanceContent content) {
        return new GuidanceCandidate(content.getTitle(), content.getDescription());
    }
}
