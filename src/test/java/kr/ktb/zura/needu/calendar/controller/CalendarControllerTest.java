package kr.ktb.zura.needu.calendar.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import kr.ktb.zura.needu.calendar.dto.response.CalendarEventDetailResponse;
import kr.ktb.zura.needu.calendar.dto.response.CalendarEventIndicatorResponse;
import kr.ktb.zura.needu.calendar.dto.response.CalendarEventResponse;
import kr.ktb.zura.needu.calendar.dto.response.DailyCalendarEventResponse;
import kr.ktb.zura.needu.calendar.dto.response.MonthlyCalendarEventResponse;
import kr.ktb.zura.needu.calendar.exception.CalendarErrorCode;
import kr.ktb.zura.needu.calendar.service.CalendarEventCommandService;
import kr.ktb.zura.needu.calendar.service.CalendarEventQueryService;
import kr.ktb.zura.needu.calendar.type.CalendarEventColor;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
    private static final Long EVENT_ID = 101L;
    private static final String VALID_UPDATE_BODY = """
            {
              "title": "생일 선물 구매",
              "description": "백화점에서 향수 구매",
              "startAt": "2026-10-30T20:00:00+09:00",
              "endAt": "2026-10-30T21:00:00+09:00",
              "allDay": false,
              "color": "RED",
              "notificationEnabled": true
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

    @Test
    void validRequest_updateCalendarEvent_returnsOkWithEventId() throws Exception {
        given(calendarEventCommandService.updateCalendarEvent(eq(USER_ID), eq(EVENT_ID), any()))
                .willReturn(new CalendarEventResponse(EVENT_ID));

        mockMvc.perform(patchCalendarEvent(VALID_UPDATE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("캘린더 일정을 수정했습니다."))
                .andExpect(jsonPath("$.data.eventId").value(EVENT_ID));
    }

    @Test
    void onlyColorSent_updateCalendarEvent_returnsOk() throws Exception {
        given(calendarEventCommandService.updateCalendarEvent(eq(USER_ID), eq(EVENT_ID), any()))
                .willReturn(new CalendarEventResponse(EVENT_ID));

        mockMvc.perform(patchCalendarEvent("{\"color\": \"RED\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void onlyAllDayOrNotificationEnabledSent_updateCalendarEvent_returnsOk() throws Exception {
        given(calendarEventCommandService.updateCalendarEvent(eq(USER_ID), eq(EVENT_ID), any()))
                .willReturn(new CalendarEventResponse(EVENT_ID));

        mockMvc.perform(patchCalendarEvent("{\"allDay\": true}"))
                .andExpect(status().isOk());
        mockMvc.perform(patchCalendarEvent("{\"notificationEnabled\": false}"))
                .andExpect(status().isOk());
    }

    @Test
    void allDayNotBoolean_updateCalendarEvent_returnsBadRequest() throws Exception {
        mockMvc.perform(patchCalendarEvent("{\"allDay\": \"yes\"}"))
                .andExpect(status().isBadRequest());

        verify(calendarEventCommandService, never()).updateCalendarEvent(anyLong(), anyLong(), any());
    }

    @Test
    void emptyBody_updateCalendarEvent_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(patchCalendarEvent("{}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("입력 값이 유효하지 않습니다. 입력한 내용을 확인해 주세요."));

        verify(calendarEventCommandService, never()).updateCalendarEvent(anyLong(), anyLong(), any());
    }

    @Test
    void onlyStartAtSent_updateCalendarEvent_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(patchCalendarEvent("{\"startAt\": \"2026-10-30T20:00:00+09:00\"}"))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(patchCalendarEvent("{\"endAt\": \"2026-10-30T21:00:00+09:00\"}"))
                .andExpect(status().isUnprocessableContent());

        verify(calendarEventCommandService, never()).updateCalendarEvent(anyLong(), anyLong(), any());
    }

    @Test
    void blankOrTooLongTitle_updateCalendarEvent_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(patchCalendarEvent("{\"title\": \" \"}"))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(patchCalendarEvent("{\"title\": \"" + "가".repeat(51) + "\"}"))
                .andExpect(status().isUnprocessableContent());

        verify(calendarEventCommandService, never()).updateCalendarEvent(anyLong(), anyLong(), any());
    }

    @Test
    void eventIdNotNumber_updateCalendarEvent_returnsBadRequest() throws Exception {
        mockMvc.perform(patch(URL + "/kakao-event-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_UPDATE_BODY)
                        .with(authenticatedUser())
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        verify(calendarEventCommandService, never()).updateCalendarEvent(anyLong(), anyLong(), any());
    }

    @Test
    void eventNotFound_updateCalendarEvent_returnsNotFound() throws Exception {
        given(calendarEventCommandService.updateCalendarEvent(eq(USER_ID), eq(EVENT_ID), any()))
                .willThrow(new BusinessException(CalendarErrorCode.CALENDAR_EVENT_NOT_FOUND));

        mockMvc.perform(patchCalendarEvent(VALID_UPDATE_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("캘린더 일정을 찾을 수 없습니다."))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void ownEvent_deleteCalendarEvent_returnsNoContent() throws Exception {
        mockMvc.perform(deleteCalendarEvent())
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(calendarEventCommandService).deleteCalendarEvent(USER_ID, EVENT_ID);
    }

    @Test
    void csrfMissing_deleteCalendarEvent_returnsForbidden() throws Exception {
        mockMvc.perform(delete(URL + "/" + EVENT_ID).with(authenticatedUser()))
                .andExpect(status().isForbidden());

        verify(calendarEventCommandService, never()).deleteCalendarEvent(anyLong(), anyLong());
    }

    @Test
    void eventIdNotNumber_deleteCalendarEvent_returnsBadRequest() throws Exception {
        mockMvc.perform(delete(URL + "/kakao-event-id").with(authenticatedUser()).with(csrf()))
                .andExpect(status().isBadRequest());

        verify(calendarEventCommandService, never()).deleteCalendarEvent(anyLong(), anyLong());
    }

    @Test
    void eventNotFound_deleteCalendarEvent_returnsNotFound() throws Exception {
        willThrow(new BusinessException(CalendarErrorCode.CALENDAR_EVENT_NOT_FOUND))
                .given(calendarEventCommandService).deleteCalendarEvent(USER_ID, EVENT_ID);

        mockMvc.perform(deleteCalendarEvent())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("캘린더 일정을 찾을 수 없습니다."))
                .andExpect(jsonPath("$.data").isEmpty());
    }

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

    @Test
    void findCalendarEvent_returnsDetailInCommonResponse() throws Exception {
        given(calendarEventQueryService.findCalendarEvent(1L, 123L))
                .willReturn(new CalendarEventDetailResponse(123L, "생일 선물 준비", "선물 구매",
                        LocalDateTime.of(2026, 10, 30, 1, 0), LocalDateTime.of(2026, 10, 30, 2, 0), false, true,
                        CalendarEventColor.ORANGE));

        mockMvc.perform(get(URL + "/123")
                        .with(authentication(new UsernamePasswordAuthenticationToken(1L, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("캘린더 일정을 조회했습니다."))
                .andExpect(jsonPath("$.data.eventId").value(123))
                .andExpect(jsonPath("$.data.title").value("생일 선물 준비"))
                .andExpect(jsonPath("$.data.description").value("선물 구매"))
                .andExpect(jsonPath("$.data.startAt").value("2026-10-30T01:00:00Z"))
                .andExpect(jsonPath("$.data.endAt").value("2026-10-30T02:00:00Z"))
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

    private static MockHttpServletRequestBuilder deleteCalendarEvent() {
        return delete(URL + "/" + EVENT_ID)
                .with(authenticatedUser())
                .with(csrf());
    }

    private static MockHttpServletRequestBuilder patchCalendarEvent(String body) {
        return patch(URL + "/" + EVENT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(authenticatedUser())
                .with(csrf());
    }

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
