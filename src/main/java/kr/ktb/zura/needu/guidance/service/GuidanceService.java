package kr.ktb.zura.needu.guidance.service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import kr.ktb.zura.needu.guidance.dto.response.GuidanceResponse;
import kr.ktb.zura.needu.guidance.service.candidate.GuidanceCandidate;
import kr.ktb.zura.needu.guidance.service.candidate.GuidanceCandidateProvider;
import kr.ktb.zura.needu.guidance.type.GuidanceContent;
import kr.ktb.zura.needu.user.dto.response.UserSummaryResponse;
import kr.ktb.zura.needu.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GuidanceService {

    private final UserService userService;
    private final List<GuidanceCandidateProvider> candidateProviders;

    public GuidanceResponse findGuidance(Long userId) {
        UserSummaryResponse userSummary = userService.findUserSummary(userId);

        if (!userSummary.tasteAnalysisCompleted()) {
            return GuidanceResponse.from(false, GuidanceCandidate.from(GuidanceContent.TASTE_ANALYSIS_REQUIRED));
        }
        return GuidanceResponse.from(true, selectRandomCandidate(findAllCandidates(userSummary)));
    }

    private List<GuidanceCandidate> findAllCandidates(UserSummaryResponse user) {
        return candidateProviders.stream()
                .flatMap(provider -> provider.findCandidates(user).stream())
                .toList();
    }

    private GuidanceCandidate selectRandomCandidate(List<GuidanceCandidate> candidates) {
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }
}
