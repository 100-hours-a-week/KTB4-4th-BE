package kr.ktb.zura.needu.aichat.service;

import java.util.List;

import kr.ktb.zura.needu.aichat.client.dto.response.AiServerAnalysisKeywordResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerCloseSessionResponse;
import kr.ktb.zura.needu.product.service.ProductRecommendationService;
import kr.ktb.zura.needu.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// AI 세션을 닫은 뒤의 저장(추천 상품, 취향 프로필, 대화방 완료)을 하나의 트랜잭션으로
// <- 일부만 저장된 채 대화방이 ANALYZING으로 남으면 닫힌 세션으로는 다시 확정할 수 없음
@Service
@RequiredArgsConstructor
public class AiAnalysisConfirmService {

    private final ProductRecommendationService productRecommendationService;
    private final UserService userService;
    private final AiChatRoomService aiChatRoomService;

    @Transactional
    public void saveConfirmedAnalysis(Long userId, Long conversationId, AiServerCloseSessionResponse response) {
        List<String> tastes = toKeywordValues(response.keywords().taste());
        List<String> interests = toKeywordValues(response.keywords().interest());
        productRecommendationService.saveRecommendations(
                userId,
                response.recommendations().self(),
                response.recommendations().gift(),
                tastes);
        userService.completeTasteAnalysis(userId, response.summary(), tastes, interests);
        aiChatRoomService.completeRoom(conversationId);
    }

    private List<String> toKeywordValues(List<AiServerAnalysisKeywordResponse> keywords) {
        return keywords.stream().map(AiServerAnalysisKeywordResponse::value).toList();
    }
}
