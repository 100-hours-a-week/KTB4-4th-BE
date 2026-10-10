package kr.ktb.zura.needu.user.controller;

import java.time.LocalDateTime;
import java.util.List;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.user.dto.request.ConsentAgreementRequest;
import kr.ktb.zura.needu.user.dto.request.UpdateConsentRequest;
import kr.ktb.zura.needu.user.dto.response.ConsentResponse;
import kr.ktb.zura.needu.user.dto.response.ConsentsResponse;
import kr.ktb.zura.needu.user.service.ConsentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConsentController.class)
class ConsentControllerTest {

    private static final Long LOGIN_USER_ID = 1L;
    private static final String URL = "/api/v1/users/me/consents";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConsentService consentService;

    @Test
    void loggedInUser_findConsents_returnsConsentsWithAgreement() throws Exception {
        given(consentService.findConsents(LOGIN_USER_ID)).willReturn(new ConsentsResponse(List.of(
                new ConsentResponse(1L, "개인정보 수집 및 이용 동의", "내용", true, "1.0", true,
                        LocalDateTime.of(2026, 10, 1, 9, 0)),
                new ConsentResponse(2L, "AI 대화 정보 활용 동의", "내용", true, "1.0", false, null))));

        mockMvc.perform(get(URL).with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("동의 항목을 조회했습니다."))
                .andExpect(jsonPath("$.data.consents[0].id").value(1))
                .andExpect(jsonPath("$.data.consents[0].required").value(true))
                .andExpect(jsonPath("$.data.consents[0].version").value("1.0"))
                .andExpect(jsonPath("$.data.consents[0].agreed").value(true))
                .andExpect(jsonPath("$.data.consents[0].agreedAt").value("2026-10-01T09:00:00"))
                .andExpect(jsonPath("$.data.consents[1].agreed").value(false))
                .andExpect(jsonPath("$.data.consents[1].agreedAt").isEmpty());
    }

    @Test
    void validRequest_updateConsents_returnsSaved() throws Exception {
        mockMvc.perform(put(URL)
                        .with(authenticatedUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"consents": [{"id": 1, "agreed": true}, {"id": 2, "agreed": true}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("동의 내용을 저장했습니다."))
                .andExpect(jsonPath("$.data").isEmpty());

        verify(consentService).updateConsents(LOGIN_USER_ID, new UpdateConsentRequest(List.of(
                new ConsentAgreementRequest(1L, true),
                new ConsentAgreementRequest(2L, true))));
    }

    @Test
    void emptyConsents_updateConsents_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(put(URL)
                        .with(authenticatedUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"consents": []}
                                """))
                .andExpect(status().isUnprocessableContent());

        verify(consentService, never()).updateConsents(any(), any());
    }

    @Test
    void missingAgreed_updateConsents_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(put(URL)
                        .with(authenticatedUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"consents": [{"id": 1}]}
                                """))
                .andExpect(status().isUnprocessableContent());

        verify(consentService, never()).updateConsents(any(), any());
    }

    @Test
    void agreedNotBoolean_updateConsents_returnsBadRequest() throws Exception {
        mockMvc.perform(put(URL)
                        .with(authenticatedUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"consents": [{"id": 1, "agreed": "yes"}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(CommonErrorCode.COMMON_INVALID_REQUEST.getMessage()));
    }

    @Test
    void requiredConsentDisagreed_updateConsents_returnsUnprocessableContent() throws Exception {
        willThrow(new BusinessException(CommonErrorCode.COMMON_INVALID_INPUT))
                .given(consentService).updateConsents(eq(LOGIN_USER_ID), any());

        mockMvc.perform(put(URL)
                        .with(authenticatedUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"consents": [{"id": 1, "agreed": false}]}
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value(CommonErrorCode.COMMON_INVALID_INPUT.getMessage()))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    private static RequestPostProcessor authenticatedUser() {
        return authentication(new UsernamePasswordAuthenticationToken(LOGIN_USER_ID, null, List.of()));
    }
}
