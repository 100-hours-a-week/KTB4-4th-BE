package kr.ktb.zura.needu.aichat.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import kr.ktb.zura.needu.aichat.dto.request.SendMessageRequest;
import kr.ktb.zura.needu.aichat.dto.response.AiConversationResponse;
import kr.ktb.zura.needu.aichat.dto.response.AiMessageResponse;
import kr.ktb.zura.needu.aichat.dto.response.AiMessageSummaryResponse;
import kr.ktb.zura.needu.aichat.dto.response.AnalysisKeywordsResponse;
import kr.ktb.zura.needu.aichat.dto.response.AnalysisKeywordResponse;
import kr.ktb.zura.needu.aichat.dto.response.AnalysisResultResponse;
import kr.ktb.zura.needu.aichat.dto.response.ProductRecommendationStatusResponse;
import kr.ktb.zura.needu.aichat.exception.AiChatErrorCode;
import kr.ktb.zura.needu.aichat.facade.AiChatFacade;
import kr.ktb.zura.needu.aichat.facade.AiConversationStartResult;
import kr.ktb.zura.needu.aichat.type.AiChatRoomStatus;
import kr.ktb.zura.needu.aichat.type.SenderType;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.common.exception.TooManyRequestsException;
import kr.ktb.zura.needu.common.response.CursorPageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiChatController.class)
class AiChatControllerTest {

    private static final Long USER_ID = 1L;
    private static final Long CONVERSATION_ID = 101L;
    private static final String URL = "/api/v1/ai/conversations";
    private static final String MESSAGES_URL = URL + "/" + CONVERSATION_ID + "/messages";
    private static final String ANALYSIS_URL = URL + "/" + CONVERSATION_ID + "/analysis";
    private static final UUID CLIENT_MESSAGE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String USER_CONTENT = "요즘 러닝에 관심이 생겼어.";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiChatFacade aiChatFacade;

    @Test
    void newConversation_startOrResumeConversation_returnsCreated() throws Exception {
        given(aiChatFacade.startOrResumeConversation(USER_ID)).willReturn(AiConversationStartResult.created(
                new AiConversationResponse(101L, AiChatRoomStatus.ACTIVE)));

        mockMvc.perform(postConversation())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("AI 대화를 시작했습니다."))
                .andExpect(jsonPath("$.data.conversationId").value(101))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.expiresAt").doesNotExist());
    }

    @Test
    void existingConversation_startOrResumeConversation_returnsOk() throws Exception {
        given(aiChatFacade.startOrResumeConversation(USER_ID)).willReturn(AiConversationStartResult.resumed(
                new AiConversationResponse(101L, AiChatRoomStatus.ANALYZING)));

        mockMvc.perform(postConversation())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("AI 대화를 조회했습니다."))
                .andExpect(jsonPath("$.data.conversationId").value(101))
                .andExpect(jsonPath("$.data.status").value("ANALYZING"));
    }

    @Test
    void aiServerUnavailable_startOrResumeConversation_returnsServiceUnavailable() throws Exception {
        given(aiChatFacade.startOrResumeConversation(USER_ID))
                .willThrow(new BusinessException(AiChatErrorCode.AICHAT_REQUEST_TIMEOUT));

        mockMvc.perform(postConversation())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("일시적으로 서비스를 이용할 수 없습니다."))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void unauthenticated_startOrResumeConversation_returnsUnauthorized() throws Exception {
        mockMvc.perform(post(URL).with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validRequest_sendMessage_returnsOk() throws Exception {
        given(aiChatFacade.sendMessage(eq(USER_ID), eq(CONVERSATION_ID), any(SendMessageRequest.class)))
                .willReturn(new AiMessageResponse(
                        201L,
                        202L,
                        "러닝을 좋아하시는군요.",
                        15,
                        true,
                        null
                ));

        mockMvc.perform(postMessage(messageBody(CLIENT_MESSAGE_ID.toString(), USER_CONTENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("메시지를 전송했습니다."))
                .andExpect(jsonPath("$.data.userMessageId").value(201))
                .andExpect(jsonPath("$.data.messageId").value(202))
                .andExpect(jsonPath("$.data.content").value("러닝을 좋아하시는군요."))
                .andExpect(jsonPath("$.data.progress").value(15))
                .andExpect(jsonPath("$.data.inputLocked").value(true))
                .andExpect(jsonPath("$.data.analysis").isEmpty());
    }

    @Test
    void blankContent_sendMessage_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(postMessage(messageBody(CLIENT_MESSAGE_ID.toString(), " ")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("입력값이 유효하지 않습니다. 입력 내용을 확인해 주세요."))
                .andExpect(jsonPath("$.data").isEmpty());
        verifyNoInteractions(aiChatFacade);
    }

    @Test
    void contentOverMaxLength_sendMessage_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(postMessage(messageBody(CLIENT_MESSAGE_ID.toString(), "가".repeat(501))))
                .andExpect(status().isUnprocessableContent());
        verifyNoInteractions(aiChatFacade);
    }

    @Test
    void missingClientMessageId_sendMessage_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(postMessage("""
                        {"content": "요즘 러닝에 관심이 생겼어."}
                        """))
                .andExpect(status().isUnprocessableContent());
        verifyNoInteractions(aiChatFacade);
    }

    @Test
    void malformedClientMessageId_sendMessage_returnsBadRequest() throws Exception {
        mockMvc.perform(postMessage(messageBody("not-a-uuid", USER_CONTENT)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청 형식이 올바르지 않습니다."));
        verifyNoInteractions(aiChatFacade);
    }

    @Test
    void conversationOwnedByAnotherUser_sendMessage_returnsForbidden() throws Exception {
        given(aiChatFacade.sendMessage(eq(USER_ID), eq(CONVERSATION_ID), any(SendMessageRequest.class)))
                .willThrow(new BusinessException(AiChatErrorCode.AICHAT_CONVERSATION_FORBIDDEN));

        mockMvc.perform(postMessage(messageBody(CLIENT_MESSAGE_ID.toString(), USER_CONTENT)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("해당 AI 대화에 접근할 수 없습니다."))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void conversationNotFound_sendMessage_returnsNotFound() throws Exception {
        given(aiChatFacade.sendMessage(eq(USER_ID), eq(CONVERSATION_ID), any(SendMessageRequest.class)))
                .willThrow(new BusinessException(AiChatErrorCode.AICHAT_CONVERSATION_NOT_FOUND));

        mockMvc.perform(postMessage(messageBody(CLIENT_MESSAGE_ID.toString(), USER_CONTENT)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("AI 대화를 찾을 수 없습니다."));
    }

    @Test
    void messageAlreadyInProgress_sendMessage_returnsTooManyRequests() throws Exception {
        given(aiChatFacade.sendMessage(eq(USER_ID), eq(CONVERSATION_ID), any(SendMessageRequest.class)))
                .willThrow(new TooManyRequestsException(10));

        mockMvc.perform(postMessage(messageBody(CLIENT_MESSAGE_ID.toString(), USER_CONTENT)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."))
                .andExpect(jsonPath("$.data.retryAfterSeconds").value(10));
    }

    @Test
    void unauthenticated_sendMessage_returnsUnauthorized() throws Exception {
        mockMvc.perform(post(MESSAGES_URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(messageBody(CLIENT_MESSAGE_ID.toString(), USER_CONTENT)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validRequest_createAnalysis_returnsAnalysis() throws Exception {
        given(aiChatFacade.createAnalysis(USER_ID, CONVERSATION_ID)).willReturn(new AnalysisResultResponse(
                "캠핑과 핸드드립을 즐깁니다.",
                new AnalysisKeywordsResponse(
                        List.of(new AnalysisKeywordResponse("핸드드립", 0.92)),
                        List.of(new AnalysisKeywordResponse("캠핑", 0.95))),
                true));

        mockMvc.perform(post(ANALYSIS_URL)
                        .with(authentication(new UsernamePasswordAuthenticationToken(USER_ID, null, List.of())))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("취향 분석이 완료되었습니다."))
                .andExpect(jsonPath("$.data.summary").value("캠핑과 핸드드립을 즐깁니다."))
                .andExpect(jsonPath("$.data.keywords.taste[0].value").value("핸드드립"))
                .andExpect(jsonPath("$.data.keywords.interest[0].value").value("캠핑"))
                .andExpect(jsonPath("$.data.correctionAvailable").value(true));
    }

    @Test
    void validRequest_findAllMessages_returnsOldestFirstMessages() throws Exception {
        given(aiChatFacade.findAllMessages(USER_ID, CONVERSATION_ID, null, 20))
                .willReturn(new CursorPageResponse<>(
                        List.of(
                                new AiMessageSummaryResponse(101L, SenderType.USER, USER_CONTENT,
                                        LocalDateTime.of(2026, 9, 5, 21, 30, 15)),
                                new AiMessageSummaryResponse(102L, SenderType.AI, "러닝을 좋아하시는군요.",
                                        LocalDateTime.of(2026, 9, 5, 21, 30, 17))
                        ),
                        "eyJtZXNzYWdlSWQiOjEwMX0",
                        true
                ));

        mockMvc.perform(getMessages("?size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("대화 메시지를 조회했습니다."))
                .andExpect(jsonPath("$.data.items[0].messageId").value(101))
                .andExpect(jsonPath("$.data.items[0].role").value("USER"))
                .andExpect(jsonPath("$.data.items[0].content").value(USER_CONTENT))
                .andExpect(jsonPath("$.data.items[0].createdAt").value("2026-09-05T21:30:15"))
                .andExpect(jsonPath("$.data.items[1].messageId").value(102))
                .andExpect(jsonPath("$.data.items[1].role").value("AI"))
                .andExpect(jsonPath("$.nextCursor").value("eyJtZXNzYWdlSWQiOjEwMX0"))
                .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    void lastPage_findAllMessages_returnsNullCursor() throws Exception {
        given(aiChatFacade.findAllMessages(USER_ID, CONVERSATION_ID, "cursor", 20))
                .willReturn(new CursorPageResponse<>(List.of(), null, false));

        mockMvc.perform(getMessages("?cursor=cursor&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.nextCursor").doesNotExist())
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void missingSize_findAllMessages_returnsBadRequest() throws Exception {
        mockMvc.perform(getMessages(""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청 형식이 올바르지 않습니다."));
        verifyNoInteractions(aiChatFacade);
    }

    @Test
    void sizeOverMax_findAllMessages_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(getMessages("?size=51"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("입력값이 유효하지 않습니다. 입력 내용을 확인해 주세요."));
        verifyNoInteractions(aiChatFacade);
    }

    @Test
    void nonNumericSize_findAllMessages_returnsBadRequest() throws Exception {
        mockMvc.perform(getMessages("?size=many"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(aiChatFacade);
    }

    @Test
    void invalidCursor_findAllMessages_returnsBadRequest() throws Exception {
        given(aiChatFacade.findAllMessages(USER_ID, CONVERSATION_ID, "broken", 20))
                .willThrow(new BusinessException(CommonErrorCode.COMMON_INVALID_REQUEST));

        mockMvc.perform(getMessages("?cursor=broken&size=20"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청 형식이 올바르지 않습니다."));
    }

    @Test
    void conversationOwnedByAnotherUser_findAllMessages_returnsForbidden() throws Exception {
        given(aiChatFacade.findAllMessages(eq(USER_ID), eq(CONVERSATION_ID), isNull(), eq(20)))
                .willThrow(new BusinessException(AiChatErrorCode.AICHAT_CONVERSATION_FORBIDDEN));

        mockMvc.perform(getMessages("?size=20"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("해당 AI 대화에 접근할 수 없습니다."));
    }

    @Test
    void conversationNotFound_findAllMessages_returnsNotFound() throws Exception {
        given(aiChatFacade.findAllMessages(eq(USER_ID), eq(CONVERSATION_ID), isNull(), eq(20)))
                .willThrow(new BusinessException(AiChatErrorCode.AICHAT_CONVERSATION_NOT_FOUND));

        mockMvc.perform(getMessages("?size=20"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("AI 대화를 찾을 수 없습니다."));
    }

    @Test
    void unauthenticated_findAllMessages_returnsUnauthorized() throws Exception {
        mockMvc.perform(get(MESSAGES_URL + "?size=20"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validRequest_confirmAnalysis_confirmsConversation() throws Exception {
        given(aiChatFacade.confirmAnalysis(USER_ID, CONVERSATION_ID))
                .willReturn(ProductRecommendationStatusResponse.success());

        mockMvc.perform(post(URL + "/" + CONVERSATION_ID + "/confirm")
                        .with(authentication(new UsernamePasswordAuthenticationToken(USER_ID, null, List.of())))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("취향 분석을 확정했습니다."))
                .andExpect(jsonPath("$.data.isRecommendationCompleted").value(true));
    }

    private static MockHttpServletRequestBuilder getMessages(String query) {
        return get(MESSAGES_URL + query)
                .with(authentication(new UsernamePasswordAuthenticationToken(USER_ID, null, List.of())));
    }

    private static String messageBody(String clientMessageId, String content) {
        return """
                {"clientMessageId": "%s", "content": "%s"}
                """.formatted(clientMessageId, content);
    }

    private static MockHttpServletRequestBuilder postMessage(String body) {
        return post(MESSAGES_URL)
                .with(authentication(new UsernamePasswordAuthenticationToken(USER_ID, null, List.of())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private static MockHttpServletRequestBuilder postConversation() {
        return post(URL)
                .with(authentication(new UsernamePasswordAuthenticationToken(USER_ID, null, List.of())))
                .with(csrf());
    }
}
