package kr.ktb.zura.needu.guidance.service.candidate;

import java.util.List;

import kr.ktb.zura.needu.guidance.type.GuidanceContent;
import kr.ktb.zura.needu.user.dto.response.UserSummaryResponse;
import org.springframework.stereotype.Component;

@Component
public class RecommendationGuidanceCandidateProvider implements GuidanceCandidateProvider {

    @Override
    public List<GuidanceCandidate> findCandidates(UserSummaryResponse user) {
        return List.of(GuidanceCandidate.from(GuidanceContent.TASTE_ANALYSIS_COMPLETED));
    }
}
