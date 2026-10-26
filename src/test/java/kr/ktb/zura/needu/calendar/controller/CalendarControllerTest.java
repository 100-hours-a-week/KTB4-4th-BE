package kr.ktb.zura.needu.calendar.controller;

import java.time.LocalDate;
import java.util.List;
import kr.ktb.zura.needu.calendar.dto.response.DailyCalendarEventResponse;
import kr.ktb.zura.needu.calendar.service.CalendarEventCommandService;
import kr.ktb.zura.needu.calendar.service.CalendarEventQueryService;
import kr.ktb.zura.needu.calendar.type.CalendarEventColor;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;
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
    void dailyEvents_returnsTitlesAndNullableColors() throws Exception {
        given(calendarEventQueryService.findAllDailyCalendarEvents(1L, LocalDate.of(2026, 10, 30)))
                .willReturn(List.of(new DailyCalendarEventResponse(123L, "생일 선물 준비", CalendarEventColor.ORANGE),
                        new DailyCalendarEventResponse(124L, "저녁 약속", null)));

        mockMvc.perform(get(URL + "/daily").param("date", "2026-10-30")
                        .with(authentication(new UsernamePasswordAuthenticationToken(1L, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].eventId").value(123))
                .andExpect(jsonPath("$.data.items[0].title").value("생일 선물 준비"))
                .andExpect(jsonPath("$.data.items[0].color").value("ORANGE"))
                .andExpect(jsonPath("$.data.items[1].color").value(org.hamcrest.Matchers.nullValue()));
    }

    @ParameterizedTest
    @EnumSource(value = UserErrorCode.class, names = {"USER_BLOCKED", "USER_ONBOARDING_REQUIRED"})
    void dailyEvents_invalidUser_returnsForbidden(UserErrorCode error) throws Exception {
        given(calendarEventQueryService.findAllDailyCalendarEvents(1L, LocalDate.of(2026, 10, 30)))
                .willThrow(new BusinessException(error));

        mockMvc.perform(get(URL + "/daily").param("date", "2026-10-30")
                        .with(authentication(new UsernamePasswordAuthenticationToken(1L, null, List.of()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(error.getMessage()));
    }

    @Test
    void dailyEvents_unauthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(get(URL + "/daily").param("date", "2026-10-30"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(calendarEventQueryService);
    }

    @Test
    void validDailyDate_returnsCalendarEvents() throws Exception {
        given(calendarEventQueryService.findAllDailyCalendarEvents(1L, LocalDate.of(2028, 2, 29)))
                .willReturn(List.of());

        mockMvc.perform(get(URL + "/daily").param("date", "2028-02-29")
                        .with(authentication(new UsernamePasswordAuthenticationToken(1L, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("캘린더 일정 목록을 조회했습니다."))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10", "2026/10/26", "2026-1-1", "2026-02-29", "2026-02-30",
            "2026-13-01", "2026-10-26T12:00:00", "2026-10-26+09:00", "invalid", "",
            " 2026-10-26", "2026-10-26 "})
    void invalidDailyDate_returnsCommonBadRequest(String date) throws Exception {
        mockMvc.perform(get(URL + "/daily").param("date", date)
                        .with(authentication(new UsernamePasswordAuthenticationToken(1L, null, List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청 형식이 올바르지 않습니다."))
                .andExpect(jsonPath("$.data").isEmpty());

        verifyNoInteractions(calendarEventQueryService);
    }

    @Test
    void missingDailyDate_returnsCommonBadRequest() throws Exception {
        mockMvc.perform(get(URL + "/daily")
                        .with(authentication(new UsernamePasswordAuthenticationToken(1L, null, List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청 형식이 올바르지 않습니다."))
                .andExpect(jsonPath("$.data").isEmpty());

        verifyNoInteractions(calendarEventQueryService);
    }
}
