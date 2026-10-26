package kr.ktb.zura.needu.calendar.controller;

import java.util.List;

import kr.ktb.zura.needu.calendar.dto.response.CalendarEventResponse;
import kr.ktb.zura.needu.calendar.service.CalendarEventCommandService;
import kr.ktb.zura.needu.calendar.service.CalendarEventQueryService;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
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

@WebMvcTest(CalendarController.class)
class CalendarControllerTest {

    private static final Long USER_ID = 1L;
    private static final String URL = "/api/v1/calendar/events";
    private static final String VALID_BODY = """
            {
              "title": "생일 선물 준비",
              "startAt": "2026-10-30T19:00:00+09:00",
              "endAt": "2026-10-30T20:00:00+09:00",
              "allDay": false,
              "description": "선물 구매"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CalendarEventCommandService calendarEventCommandService;

    @MockitoBean
    private CalendarEventQueryService calendarEventQueryService;

    @Test
    void validRequest_createCalendarEvent_returnsCreatedWithEventId() throws Exception {
        given(calendarEventCommandService.createCalendarEvent(eq(USER_ID), any()))
                .willReturn(new CalendarEventResponse(101L));

        mockMvc.perform(postCalendarEvent(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("캘린더 일정을 등록했습니다."))
                .andExpect(jsonPath("$.data.eventId").value(101));
    }

    @Test
    void optionalFieldsIncluded_createCalendarEvent_returnsCreated() throws Exception {
        given(calendarEventCommandService.createCalendarEvent(eq(USER_ID), any()))
                .willReturn(new CalendarEventResponse(101L));
        String body = VALID_BODY.replace("\"description\": \"선물 구매\"",
                "\"description\": \"선물 구매\", \"color\": \"RED\", \"notificationEnabled\": true");

        mockMvc.perform(postCalendarEvent(body))
                .andExpect(status().isCreated());
    }

    @Test
    void titleOver50Characters_createCalendarEvent_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(postCalendarEvent(VALID_BODY.replace("생일 선물 준비", "가".repeat(51))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("입력 값이 유효하지 않습니다. 입력한 내용을 확인해 주세요."));

        verify(calendarEventCommandService, never()).createCalendarEvent(anyLong(), any());
    }

    @Test
    void requiredFieldMissing_createCalendarEvent_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(postCalendarEvent(VALID_BODY.replace("\"title\": \"생일 선물 준비\"", "\"title\": \" \"")))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(postCalendarEvent(VALID_BODY.replace("\"startAt\": \"2026-10-30T19:00:00+09:00\",", "")))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(postCalendarEvent(VALID_BODY.replace("\"allDay\": false,", "")))
                .andExpect(status().isUnprocessableContent());

        verify(calendarEventCommandService, never()).createCalendarEvent(anyLong(), any());
    }

    @Test
    void invalidDateFormat_createCalendarEvent_returnsBadRequest() throws Exception {
        mockMvc.perform(postCalendarEvent(VALID_BODY.replace("2026-10-30T19:00:00+09:00", "2026/10/30 19:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청 형식이 올바르지 않습니다."));

        verify(calendarEventCommandService, never()).createCalendarEvent(anyLong(), any());
    }

    @Test
    void unsupportedColor_createCalendarEvent_returnsBadRequest() throws Exception {
        String body = VALID_BODY.replace("\"description\": \"선물 구매\"",
                "\"description\": \"선물 구매\", \"color\": \"GOLD\"");

        mockMvc.perform(postCalendarEvent(body))
                .andExpect(status().isBadRequest());

        verify(calendarEventCommandService, never()).createCalendarEvent(anyLong(), any());
    }

    @Test
    void endBeforeStart_createCalendarEvent_returnsUnprocessableContent() throws Exception {
        given(calendarEventCommandService.createCalendarEvent(eq(USER_ID), any()))
                .willThrow(new BusinessException(CommonErrorCode.COMMON_INVALID_INPUT));

        mockMvc.perform(postCalendarEvent(VALID_BODY))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void onboardingRequiredUser_createCalendarEvent_returnsForbidden() throws Exception {
        given(calendarEventCommandService.createCalendarEvent(eq(USER_ID), any()))
                .willThrow(new BusinessException(UserErrorCode.USER_ONBOARDING_REQUIRED));

        mockMvc.perform(postCalendarEvent(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("온보딩 진행이 필요합니다."));
    }

    private static MockHttpServletRequestBuilder postCalendarEvent(String body) {
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
