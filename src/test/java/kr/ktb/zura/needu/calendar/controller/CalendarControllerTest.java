package kr.ktb.zura.needu.calendar.controller;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import kr.ktb.zura.needu.calendar.dto.response.CalendarEventIndicatorResponse;
import kr.ktb.zura.needu.calendar.dto.response.MonthlyCalendarEventResponse;
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
    void validMonth_returnsSummaryInCommonResponse() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 30);
        var indicator = new CalendarEventIndicatorResponse(123L, date, date.plusDays(1), CalendarEventColor.ORANGE);
        given(calendarEventQueryService.findMonthlyCalendarEventSummary(1L, YearMonth.of(2026, 10)))
                .willReturn(List.of(new MonthlyCalendarEventResponse(date, 1, List.of(indicator))));

        mockMvc.perform(get(URL).param("targetMonth", "2026-10")
                        .with(authentication(new UsernamePasswordAuthenticationToken(1L, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("캘린더 일정 요약을 조회했습니다."))
                .andExpect(jsonPath("$.data.items[0].date").value("2026-10-30"))
                .andExpect(jsonPath("$.data.items[0].eventCount").value(1))
                .andExpect(jsonPath("$.data.items[0].indicators[0].eventId").value(123))
                .andExpect(jsonPath("$.data.items[0].indicators[0].startDate").value("2026-10-30"))
                .andExpect(jsonPath("$.data.items[0].indicators[0].endDate").value("2026-10-31"))
                .andExpect(jsonPath("$.data.items[0].indicators[0].color").value("ORANGE"));
    }

    @Test
    void blockedUser_returnsForbidden() throws Exception {
        given(calendarEventQueryService.findMonthlyCalendarEventSummary(1L, YearMonth.of(2026, 10)))
                .willThrow(new BusinessException(UserErrorCode.USER_BLOCKED));

        mockMvc.perform(get(URL).param("targetMonth", "2026-10")
                        .with(authentication(new UsernamePasswordAuthenticationToken(1L, null, List.of()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("이용이 제한된 계정입니다."))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void noEvents_returnsEmptyItems() throws Exception {
        given(calendarEventQueryService.findMonthlyCalendarEventSummary(1L, YearMonth.of(2026, 10)))
                .willReturn(List.of());

        mockMvc.perform(get(URL).param("targetMonth", "2026-10")
                        .with(authentication(new UsernamePasswordAuthenticationToken(1L, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-13", "2026-00", "2026-10-01", "invalid", "", "2026-1", "26-10",
            "2026/10", "2026-10 ", " 2026-10", "2026-10\n"})
    void invalidMonth_returnsBadRequest(String month) throws Exception {
        mockMvc.perform(get(URL).param("targetMonth", month)
                        .with(authentication(new UsernamePasswordAuthenticationToken(1L, null, List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청 형식이 올바르지 않습니다."))
                .andExpect(jsonPath("$.data").isEmpty());

        verifyNoInteractions(calendarEventQueryService);
    }

    @Test
    void missingMonth_returnsBadRequest() throws Exception {
        mockMvc.perform(get(URL)
                        .with(authentication(new UsernamePasswordAuthenticationToken(1L, null, List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청 형식이 올바르지 않습니다."))
                .andExpect(jsonPath("$.data").isEmpty());

        verifyNoInteractions(calendarEventQueryService);
    }

    @Test
    void unauthenticatedRequest_isRejected() throws Exception {
        mockMvc.perform(get(URL).param("targetMonth", "2026-10"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(calendarEventQueryService);
    }
}
