package kr.ktb.zura.needu.guidance.service.candidate;

import java.util.List;

import kr.ktb.zura.needu.user.dto.response.UserSummaryResponse;

// 친구 생일, 사용자 생일, 국경일 등 메인 문구 출처가 늘어나면 GuidanceService 수정 없이 구현체만 추가해 확장하기 위함
public interface GuidanceCandidateProvider {

    List<GuidanceCandidate> findCandidates(UserSummaryResponse user);
}
