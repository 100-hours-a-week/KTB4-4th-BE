package kr.ktb.zura.needu.aichat.client;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import kr.ktb.zura.needu.aichat.client.dto.request.AiServerAnalysisKeywordsRequest;
import kr.ktb.zura.needu.aichat.client.dto.request.AiServerPatchAnalysisRequest;
import kr.ktb.zura.needu.aichat.client.dto.request.AiServerUserProfileRequest;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerAnalysisKeywordResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerAnalysisResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerCloseSessionResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerSendMessageResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerStartSessionResponse;
import kr.ktb.zura.needu.user.type.Gender;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakeAiChatClientTest {

    private static final AiServerUserProfileRequest USER_PROFILE = new AiServerUserProfileRequest(
            "니듀", LocalDate.of(2000, 1, 1), Gender.FEMALE, List.of("COFFEE_TEA"), List.of(), List.of());

    @Test
    void conversationFlow_returnsConsistentResponses() {
        AiChatClient client = new FakeAiChatClient(Duration.ZERO, 2, 20, Duration.ZERO);

        AiServerStartSessionResponse session = client.startSession(1L, 101L, USER_PROFILE);
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
        assertEquals("8255331367", closedSession.recommendations().self().items().getFirst().externalId());
        assertEquals(new BigDecimal("9.2"), closedSession.recommendations().self().items().getFirst().score());
        assertEquals("6927419268", closedSession.recommendations().gift().items().getFirst().externalId());
        assertEquals(new BigDecimal("8.0"), closedSession.recommendations().gift().items().getFirst().score());
    }

    @Test
    void configuredMaxTurns_locksInputOnLastTurn() {
        AiChatClient client = new FakeAiChatClient(Duration.ZERO, 3, 20, Duration.ZERO);

        client.startSession(1L, 101L, USER_PROFILE);
        AiServerSendMessageResponse firstReply = client.sendMessage(1L, 101L, "first");
        AiServerSendMessageResponse secondReply = client.sendMessage(1L, 101L, "second");
        AiServerSendMessageResponse thirdReply = client.sendMessage(1L, 101L, "third");

        assertEquals(33, firstReply.progress());
        assertEquals(66, secondReply.progress());
        assertEquals(100, thirdReply.progress());
        assertEquals(false, secondReply.inputLocked());
        assertEquals(true, thirdReply.inputLocked());
    }

    @Test
    void configuredLatency_delaysMockCall() {
        AiChatClient client = new FakeAiChatClient(Duration.ofMillis(30), 2, 20, Duration.ZERO);

        long startedAt = System.nanoTime();
        client.startSession(1L, 101L, USER_PROFILE);

        assertTrue(Duration.ofNanos(System.nanoTime() - startedAt).toMillis() >= 20);
    }

    @Test
    void configuredReplyChunks_streamMessage_passesReplyInChunksThenReturnsResponse() {
        AiChatClient client = new FakeAiChatClient(Duration.ZERO, 3, 2, Duration.ZERO);
        client.startSession(1L, 101L, USER_PROFILE);
        List<String> chunks = new ArrayList<>();

        AiServerSendMessageResponse response = client.streamMessage(1L, 101L, 701L, "first", chunks::add);

        assertEquals(response.reply(), String.join("", chunks));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.codePointCount(0, chunk.length()) <= 2));
        assertTrue(chunks.size() > 1);
    }

    @Test
    void invalidConfiguration_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new FakeAiChatClient(Duration.ofMillis(-1), 2, 20, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new FakeAiChatClient(Duration.ZERO, 0, 20, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new FakeAiChatClient(Duration.ZERO, 2, 0, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new FakeAiChatClient(Duration.ZERO, 2, 20, Duration.ofMillis(-1)));
    }
}
