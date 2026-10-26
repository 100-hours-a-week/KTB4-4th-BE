package kr.ktb.zura.needu.calendar.controller;

import java.time.LocalDate;
import java.util.List;
import kr.ktb.zura.needu.calendar.dto.response.CalendarEventDetailResponse;
import kr.ktb.zura.needu.calendar.exception.CalendarErrorCode;
import kr.ktb.zura.needu.calendar.service.CalendarEventCommandService;
import kr.ktb.zura.needu.calendar.service.CalendarEventQueryService;
import kr.ktb.zura.needu.calendar.type.CalendarEventColor;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CalendarController.class)
class CalendarControllerTest {

    private static final String URL = "/api/v1/calendar/events";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CalendarEventCommandService calendarEventCommandService;

    @MockitoBean
    private CalendarEventQueryService calendarEventQueryService;

    @Test
    void findCalendarEvent_returnsDetailInCommonResponse() throws Exception {
        given(calendarEventQueryService.findCalendarEvent(1L, 123L))
                .willReturn(new CalendarEventDetailResponse(123L, "생일 선물 준비", "선물 구매",
                        LocalDate.of(2026, 10, 30), LocalDate.of(2026, 10, 31), false, true, CalendarEventColor.ORANGE));

        mockMvc.perform(get(URL + "/123")
                        .with(authentication(new UsernamePasswordAuthenticationToken(1L, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("캘린더 일정을 조회했습니다."))
                .andExpect(jsonPath("$.data.eventId").value(123))
                .andExpect(jsonPath("$.data.title").value("생일 선물 준비"))
                .andExpect(jsonPath("$.data.description").value("선물 구매"))
                .andExpect(jsonPath("$.data.startDate").value("2026-10-30"))
                .andExpect(jsonPath("$.data.endDate").value("2026-10-31"))
                .andExpect(jsonPath("$.data.isAllDay").value(false))
                .andExpect(jsonPath("$.data.isNotificationEnabled").value(true))
                .andExpect(jsonPath("$.data.color").value("ORANGE"));
    }

    @Test
    void findCalendarEvent_notFound_returnsNotFound() throws Exception {
        given(calendarEventQueryService.findCalendarEvent(1L, 123L))
                .willThrow(new BusinessException(CalendarErrorCode.CALENDAR_EVENT_NOT_FOUND));

        mockMvc.perform(get(URL + "/123")
                        .with(authentication(new UsernamePasswordAuthenticationToken(1L, null, List.of()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("캘린더 일정을 찾을 수 없습니다."));
    }

    @Test
    void findCalendarEvent_blockedUser_returnsForbidden() throws Exception {
        given(calendarEventQueryService.findCalendarEvent(1L, 123L))
                .willThrow(new BusinessException(UserErrorCode.USER_BLOCKED));

        mockMvc.perform(get(URL + "/123")
                        .with(authentication(new UsernamePasswordAuthenticationToken(1L, null, List.of()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("이용이 제한된 계정입니다."));
    }

    @Test
    void findCalendarEvent_invalidId_returnsBadRequest() throws Exception {
        mockMvc.perform(get(URL + "/invalid")
                        .with(authentication(new UsernamePasswordAuthenticationToken(1L, null, List.of()))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(calendarEventQueryService);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
    void findCalendarEvent_nonPositiveId_returnsUnprocessableContent(long eventId) throws Exception {
        mockMvc.perform(get(URL + "/" + eventId)
                        .with(authentication(new UsernamePasswordAuthenticationToken(1L, null, List.of()))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("입력 값이 유효하지 않습니다. 입력한 내용을 확인해 주세요."))
                .andExpect(jsonPath("$.data").isEmpty());

        verifyNoInteractions(calendarEventQueryService);
    }

    @Test
    void findCalendarEvent_unauthenticated_isRejected() throws Exception {
        mockMvc.perform(get(URL + "/123"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(calendarEventQueryService);
    }
}
