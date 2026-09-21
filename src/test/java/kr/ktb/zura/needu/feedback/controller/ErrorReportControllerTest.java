package kr.ktb.zura.needu.feedback.controller;

import java.util.List;

import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.feedback.dto.response.ErrorReportResponse;
import kr.ktb.zura.needu.feedback.exception.FeedbackErrorCode;
import kr.ktb.zura.needu.feedback.service.ErrorReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ErrorReportController.class)
class ErrorReportControllerTest {

    private static final Long USER_ID = 1L;
    private static final String URL = "/api/v1/error-reports";
    private static final String VALID_BODY = """
            {
              "errorContext": {
                "errorType": "NETWORK",
                "errorCode": "NETWORK_DISCONNECTED",
                "occurredAt": "2026-08-26T19:40:00+09:00",
                "appVersion": "1.0.0"
              },
              "feedback": {
                "problemType": "SCREEN_NOT_DISPLAYED",
                "detail": "추천 목록이 열리지 않아요."
              }
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ErrorReportService errorReportService;

    @Test
    void validRequest_createErrorReport_returnsCreatedWithErrorReportId() throws Exception {
        given(errorReportService.createErrorReport(eq(USER_ID), any())).willReturn(new ErrorReportResponse(101L));

        mockMvc.perform(postErrorReport(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("피드백을 보내주셔서 감사합니다."))
                .andExpect(jsonPath("$.data.errorReportId").value(101));
    }

    @Test
    void nestedObjectMissing_createErrorReport_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(postErrorReport("""
                        {
                          "errorContext": {
                            "errorType": "NETWORK",
                            "errorCode": "NETWORK_DISCONNECTED",
                            "occurredAt": "2026-08-26T19:40:00+09:00",
                            "appVersion": "1.0.0"
                          }
                        }
                        """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("입력값이 유효하지 않습니다. 입력 내용을 확인해 주세요."));

        verify(errorReportService, never()).createErrorReport(anyLong(), any());
    }

    @Test
    void detailTooLarge_createErrorReport_returnsContentTooLarge() throws Exception {
        given(errorReportService.createErrorReport(eq(USER_ID), any()))
                .willThrow(new BusinessException(FeedbackErrorCode.FEEDBACK_ERROR_REPORT_TOO_LARGE));

        mockMvc.perform(postErrorReport(VALID_BODY.replace("추천 목록이 열리지 않아요.", "가".repeat(501))))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.message").value("피드백 내용이 너무 큽니다."))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void detailOver500CharactersWithOtherInvalidField_createErrorReport_returnsUnprocessableContent() throws Exception {
        String body = VALID_BODY.replace("추천 목록이 열리지 않아요.", "가".repeat(501))
                .replace("\"errorType\": \"NETWORK\",", "");

        mockMvc.perform(postErrorReport(body))
                .andExpect(status().isUnprocessableContent());

        verify(errorReportService, never()).createErrorReport(anyLong(), any());
    }

    @Test
    void requiredFieldMissing_createErrorReport_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(postErrorReport(VALID_BODY.replace("\"detail\": \"추천 목록이 열리지 않아요.\"", "\"detail\": \" \"")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("입력값이 유효하지 않습니다. 입력 내용을 확인해 주세요."));

        mockMvc.perform(postErrorReport(VALID_BODY.replace("\"errorType\": \"NETWORK\",", "")))
                .andExpect(status().isUnprocessableContent());

        verify(errorReportService, never()).createErrorReport(anyLong(), any());
    }

    @Test
    void malformedOccurredAt_createErrorReport_returnsBadRequest() throws Exception {
        mockMvc.perform(postErrorReport(VALID_BODY.replace("2026-08-26T19:40:00+09:00", "2026/08/26 19:40")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청 형식이 올바르지 않습니다."))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void duplicatedErrorReport_createErrorReport_returnsConflict() throws Exception {
        given(errorReportService.createErrorReport(eq(USER_ID), any()))
                .willThrow(new BusinessException(FeedbackErrorCode.FEEDBACK_ERROR_REPORT_DUPLICATED));

        mockMvc.perform(postErrorReport(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 전송한 피드백입니다."))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    private static MockHttpServletRequestBuilder postErrorReport(String body) {
        return post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(authenticatedUser())
                .with(csrf());
    }

    private static RequestPostProcessor authenticatedUser() {
        return authentication(new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));
    }
}
