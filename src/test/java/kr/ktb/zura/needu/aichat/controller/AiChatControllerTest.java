package kr.ktb.zura.needu.aichat.controller;

import java.util.List;

import kr.ktb.zura.needu.aichat.dto.response.AiConversationResponse;
import kr.ktb.zura.needu.aichat.exception.AiChatErrorCode;
import kr.ktb.zura.needu.aichat.facade.AiChatFacade;
import kr.ktb.zura.needu.aichat.facade.AiConversationStartResult;
import kr.ktb.zura.needu.aichat.type.AiChatRoomStatus;
import kr.ktb.zura.needu.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiChatController.class)
class AiChatControllerTest {

    private static final Long USER_ID = 1L;
    private static final String URL = "/api/v1/ai/conversations";

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

    private static MockHttpServletRequestBuilder postConversation() {
        return post(URL)
                .with(authentication(new UsernamePasswordAuthenticationToken(USER_ID, null, List.of())))
                .with(csrf());
    }
}
