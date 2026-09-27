package kr.ktb.zura.needu.aichat.service;

import java.math.BigDecimal;
import java.util.List;

import kr.ktb.zura.needu.aichat.client.dto.response.AiServerAnalysisKeywordResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerAnalysisKeywordsResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerCloseSessionResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerRecommendationResult;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerRecommendationsResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerRecommendedItem;
import kr.ktb.zura.needu.product.service.ProductRecommendationService;
import kr.ktb.zura.needu.product.type.PlatformType;
import kr.ktb.zura.needu.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AiAnalysisConfirmServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long ROOM_ID = 101L;
    private static final String SUMMARY = "캠핑을 즐깁니다.";

    @Mock
    private ProductRecommendationService productRecommendationService;

    @Mock
    private UserService userService;

    @Mock
    private AiChatRoomService aiChatRoomService;

    @InjectMocks
    private AiAnalysisConfirmService aiAnalysisConfirmService;

    @Test
    void closedSessionResult_saveConfirmedAnalysis_savesRecommendationsProfileAndCompletesRoom() {
        AiServerRecommendationResult self = recommendations("self-1");
        AiServerRecommendationResult gift = recommendations("gift-1");

        aiAnalysisConfirmService.saveConfirmedAnalysis(USER_ID, ROOM_ID, closeResponse(self, gift));

        var order = inOrder(productRecommendationService, userService, aiChatRoomService);
        order.verify(productRecommendationService).saveRecommendations(USER_ID, self, gift, List.of("실용적"));
        order.verify(userService).completeTasteAnalysis(USER_ID, SUMMARY, List.of("실용적"), List.of("캠핑"));
        order.verify(aiChatRoomService).completeRoom(ROOM_ID);
    }

    @Test
    void recommendationSaveFails_saveConfirmedAnalysis_doesNotCompleteRoom() {
        AiServerRecommendationResult self = recommendations("self-1");
        AiServerRecommendationResult gift = recommendations("gift-1");
        willThrow(new RuntimeException("save failed")).given(productRecommendationService)
                .saveRecommendations(USER_ID, self, gift, List.of("실용적"));

        assertThatThrownBy(() -> aiAnalysisConfirmService.saveConfirmedAnalysis(
                USER_ID, ROOM_ID, closeResponse(self, gift)))
                .hasMessage("save failed");

        verify(aiChatRoomService, never()).completeRoom(anyLong());
    }

    private static AiServerCloseSessionResponse closeResponse(
            AiServerRecommendationResult self, AiServerRecommendationResult gift) {
        return new AiServerCloseSessionResponse(
                ROOM_ID,
                USER_ID,
                SUMMARY,
                new AiServerAnalysisKeywordsResponse(
                        List.of(new AiServerAnalysisKeywordResponse("실용적", 0.9)),
                        List.of(new AiServerAnalysisKeywordResponse("캠핑", 0.8))),
                new AiServerRecommendationsResponse(self, gift));
    }

    private static AiServerRecommendationResult recommendations(String externalId) {
        return new AiServerRecommendationResult(List.of(
                new AiServerRecommendedItem(PlatformType.COUPANG, externalId, new BigDecimal("9.2"), "추천")));
    }
}
