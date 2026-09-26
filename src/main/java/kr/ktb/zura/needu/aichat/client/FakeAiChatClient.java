package kr.ktb.zura.needu.aichat.client;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import kr.ktb.zura.needu.aichat.client.dto.request.AiServerPatchAnalysisRequest;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerAnalysisKeywordResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerAnalysisKeywordsResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerAnalysisProfileResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerAnalysisResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerCloseSessionResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerRecommendationResult;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerRecommendedItem;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerRecommendationsResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerSendMessageResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerStartSessionResponse;
import kr.ktb.zura.needu.product.type.PlatformType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "ai.server.mock-enabled", havingValue = "true")
public class FakeAiChatClient implements AiChatClient {

    private static final String MOCK_SELF_PRODUCT_ID = "88213";
    private static final String MOCK_GIFT_PRODUCT_ID = "88214";

    private final Map<Long, Integer> mockTurns = new ConcurrentHashMap<>();
    private final Map<Long, AiServerAnalysisResponse> mockAnalyses = new ConcurrentHashMap<>();

    @Override
    public AiServerStartSessionResponse startSession(Long userId, Long conversationRoomId) {
        mockTurns.put(conversationRoomId, 0);
        return new AiServerStartSessionResponse(
                conversationRoomId,
                "안녕하세요! 요즘 어떻게 지내시는지 궁금해요.",
                OffsetDateTime.now(ZoneOffset.UTC),
                OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(30),
                20);
    }

    @Override
    public AiServerSendMessageResponse sendMessage(Long userId, Long conversationRoomId, String message) {
        int turn = mockTurns.merge(conversationRoomId, 1, Integer::sum);
        boolean inputLocked = turn >= 2;
        return new AiServerSendMessageResponse(
                inputLocked
                        ? "좋아요. 말씀해주신 내용을 바탕으로 취향을 분석해 볼게요."
                        : "흥미롭네요. 그 활동에서 가장 좋아하는 점은 무엇인가요?",
                OffsetDateTime.now(ZoneOffset.UTC),
                OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(30),
                turn,
                20,
                inputLocked,
                inputLocked,
                inputLocked ? 100 : 50);
    }

    @Override
    public AiServerAnalysisResponse createAnalysis(Long conversationRoomId) {
        AiServerAnalysisResponse response = createMockAnalysis(10293L);
        mockAnalyses.put(conversationRoomId, response);
        return response;
    }

    @Override
    public AiServerAnalysisResponse patchAnalyze(Long conversationRoomId, AiServerPatchAnalysisRequest request) {
        AiServerAnalysisResponse response = new AiServerAnalysisResponse(new AiServerAnalysisProfileResponse(
                request.userId(),
                request.summary(),
                new AiServerAnalysisKeywordsResponse(
                        toMockKeywords(request.keywords().taste()),
                        toMockKeywords(request.keywords().interest())),
                false));
        mockAnalyses.put(conversationRoomId, response);
        return response;
    }

    @Override
    public AiServerCloseSessionResponse confirmAnalysis(Long userId, Long conversationRoomId) {
        AiServerAnalysisProfileResponse profile = mockAnalyses.getOrDefault(
                conversationRoomId, createMockAnalysis(userId)).profile();
        mockAnalyses.remove(conversationRoomId);
        mockTurns.remove(conversationRoomId);
        return new AiServerCloseSessionResponse(
                conversationRoomId,
                userId,
                profile.summary(),
                profile.keywords(),
                new AiServerRecommendationsResponse(
                        new AiServerRecommendationResult(List.of(new AiServerRecommendedItem(
                                PlatformType.COUPANG,
                                MOCK_SELF_PRODUCT_ID,
                                new BigDecimal("9.2"),
                                "캠핑 취향과 잘 맞는 상품이에요."))),
                        new AiServerRecommendationResult(List.of(new AiServerRecommendedItem(
                                PlatformType.COUPANG,
                                MOCK_GIFT_PRODUCT_ID,
                                new BigDecimal("8.0"),
                                "캠핑을 좋아하는 분에게 선물하기 좋은 상품이에요.")))));
    }

    private AiServerAnalysisResponse createMockAnalysis(Long userId) {
        return new AiServerAnalysisResponse(new AiServerAnalysisProfileResponse(
                userId,
                "캠핑과 실용적인 장비를 좋아합니다.",
                new AiServerAnalysisKeywordsResponse(
                        List.of(new AiServerAnalysisKeywordResponse("실용적인 장비", 0.92)),
                        List.of(new AiServerAnalysisKeywordResponse("캠핑", 0.95))),
                true));
    }

    private List<AiServerAnalysisKeywordResponse> toMockKeywords(List<String> keywords) {
        return keywords.stream()
                .map(keyword -> new AiServerAnalysisKeywordResponse(keyword, 1.0))
                .toList();
    }

}
