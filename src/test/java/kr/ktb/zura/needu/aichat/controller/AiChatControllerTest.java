package kr.ktb.zura.needu.aichat.controller;

import java.util.List;
import java.util.UUID;

import kr.ktb.zura.needu.aichat.dto.request.SendMessageRequest;
import kr.ktb.zura.needu.aichat.dto.response.AiConversationResponse;
import kr.ktb.zura.needu.aichat.dto.response.AiMessageResponse;
import kr.ktb.zura.needu.aichat.exception.AiChatErrorCode;
import kr.ktb.zura.needu.aichat.facade.AiChatFacade;
import kr.ktb.zura.needu.aichat.facade.AiConversationStartResult;
import kr.ktb.zura.needu.aichat.type.AiChatRoomStatus;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.TooManyRequestsException;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiChatController.class)
class AiChatControllerTest {

    private static final Long USER_ID = 1L;
    private static final Long CONVERSATION_ID = 101L;
    private static final String URL = "/api/v1/ai/conversations";
    private static final String MESSAGES_URL = URL + "/" + CONVERSATION_ID + "/messages";
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
                .willReturn(new AiMessageResponse(201L, 202L, "러닝을 좋아하시는군요."));

        mockMvc.perform(postMessage(messageBody(CLIENT_MESSAGE_ID.toString(), USER_CONTENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("메시지를 전송했습니다."))
                .andExpect(jsonPath("$.data.userMessageId").value(201))
                .andExpect(jsonPath("$.data.messageId").value(202))
                .andExpect(jsonPath("$.data.content").value("러닝을 좋아하시는군요."));
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
