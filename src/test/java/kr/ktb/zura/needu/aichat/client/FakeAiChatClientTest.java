package kr.ktb.zura.needu.aichat.client;

import java.math.BigDecimal;
import java.util.List;

import kr.ktb.zura.needu.aichat.client.dto.request.AiServerAnalysisKeywordsRequest;
import kr.ktb.zura.needu.aichat.client.dto.request.AiServerPatchAnalysisRequest;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerAnalysisKeywordResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerAnalysisResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerCloseSessionResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerSendMessageResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerStartSessionResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FakeAiChatClientTest {

    @Test
    void conversationFlow_returnsConsistentResponses() {
        AiChatClient client = new FakeAiChatClient();

        AiServerStartSessionResponse session = client.startSession(1L, 101L);
        AiServerSendMessageResponse firstReply = client.sendMessage(1L, 101L, "캠핑을 좋아해요");
        AiServerSendMessageResponse secondReply = client.sendMessage(1L, 101L, "장비를 고르는 게 좋아요");
        AiServerAnalysisResponse analysis = client.createAnalysis(101L);
        AiServerAnalysisResponse patchedAnalysis = client.patchAnalyze(101L,
                new AiServerPatchAnalysisRequest(
                        1L,
                        "주말마다 자연에서 쉬는 것을 좋아합니다.",
                        new AiServerAnalysisKeywordsRequest(List.of("가벼운 장비"), List.of("자연"))));
        AiServerCloseSessionResponse closedSession = client.confirmAnalysis(1L, 101L);

        assertEquals(101L, session.conversationRoomId());
        assertEquals(50, firstReply.progress());
        assertEquals(false, firstReply.inputLocked());
        assertEquals(100, secondReply.progress());
        assertEquals(true, secondReply.inputLocked());
        assertEquals("캠핑과 실용적인 장비를 좋아합니다.", analysis.profile().summary());
        assertEquals(true, analysis.profile().correctionAvailable());
        assertEquals("주말마다 자연에서 쉬는 것을 좋아합니다.", patchedAnalysis.profile().summary());
        assertEquals(false, patchedAnalysis.profile().correctionAvailable());
        assertEquals("주말마다 자연에서 쉬는 것을 좋아합니다.", closedSession.summary());
        assertEquals(List.of(new AiServerAnalysisKeywordResponse("가벼운 장비", 1.0)),
                closedSession.keywords().taste());
        assertEquals(List.of(new AiServerAnalysisKeywordResponse("자연", 1.0)),
                closedSession.keywords().interest());
        assertEquals("88213", closedSession.recommendations().self().items().getFirst().externalId());
        assertEquals(new BigDecimal("9.2"), closedSession.recommendations().self().items().getFirst().score());
        assertEquals("88214", closedSession.recommendations().gift().items().getFirst().externalId());
        assertEquals(new BigDecimal("8.0"), closedSession.recommendations().gift().items().getFirst().score());
    }
}
