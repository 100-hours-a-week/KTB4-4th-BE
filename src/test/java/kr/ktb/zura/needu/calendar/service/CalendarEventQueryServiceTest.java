package kr.ktb.zura.needu.calendar.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import kr.ktb.zura.needu.calendar.dto.response.CalendarEventIndicatorResponse;
import kr.ktb.zura.needu.calendar.dto.response.MonthlyCalendarEventResponse;
import kr.ktb.zura.needu.calendar.repository.CalendarEventRepository;
import kr.ktb.zura.needu.calendar.type.CalendarEventColor;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import kr.ktb.zura.needu.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CalendarEventQueryServiceTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 1, 9, 0);

    @Mock
    private CalendarEventRepository calendarEventRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private CalendarEventQueryService calendarEventQueryService;

    @Test
    void overlappingEvents_groupsByDateIncludingEndDate() {
        var orange = indicator(123L, "2026-10-30", "2026-10-31");
        var blue = new CalendarEventIndicatorResponse(124L, CREATED_AT, LocalDate.parse("2026-10-31"),
                LocalDate.parse("2026-10-31"), CalendarEventColor.BLUE);
        givenEvents(YearMonth.of(2026, 10), List.of(orange, blue));

        assertThat(calendarEventQueryService.findMonthlyCalendarEventSummary(1L, YearMonth.of(2026, 10)))
                .containsExactly(
                        new MonthlyCalendarEventResponse(LocalDate.parse("2026-10-30"), 1, List.of(orange)),
                        new MonthlyCalendarEventResponse(LocalDate.parse("2026-10-31"), 2, List.of(orange, blue)));
    }

    @Test
    void spanningMonth_onlyIncludesTargetMonthAndPreservesOriginalDates() {
        var event = indicator(1L, "2028-01-31", "2028-03-01");
        YearMonth month = YearMonth.of(2028, 2);
        givenEvents(month, List.of(event));

        var result = calendarEventQueryService.findMonthlyCalendarEventSummary(1L, month);

        assertThat(result).hasSize(29);
        assertThat(result).extracting(MonthlyCalendarEventResponse::date)
                .containsExactlyElementsOf(month.atDay(1).datesUntil(month.plusMonths(1).atDay(1)).toList());
        assertThat(result).allSatisfy(summary -> {
            assertThat(summary.eventCount()).isEqualTo(1);
            assertThat(summary.indicators()).containsExactly(event);
        });
    }

    @Test
    void noEvents_returnsEmptyList() {
        YearMonth month = YearMonth.of(2026, 12);
        givenEvents(month, List.of());

        assertThat(calendarEventQueryService.findMonthlyCalendarEventSummary(1L, month)).isEmpty();
        verify(userService).validateActiveUser(1L);
    }

    @ParameterizedTest
    @EnumSource(value = UserErrorCode.class, names = {"USER_BLOCKED", "USER_ONBOARDING_REQUIRED", "USER_WITHDRAWN", "USER_NOT_FOUND"})
    void invalidUser_rejectsBeforeQueryingEvents(UserErrorCode error) {
        doThrow(new BusinessException(error)).when(userService).validateActiveUser(1L);

        assertThatThrownBy(() -> calendarEventQueryService.findMonthlyCalendarEventSummary(1L, YearMonth.of(2026, 10)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(error));

        verifyNoInteractions(calendarEventRepository);
    }

    private void givenEvents(YearMonth month, List<CalendarEventIndicatorResponse> events) {
        given(calendarEventRepository.findMonthlyEventIndicators(1L, month.atDay(1).minusDays(1).atTime(15, 0),
                month.atEndOfMonth().atTime(15, 0))).willReturn(events);
    }

    private CalendarEventIndicatorResponse indicator(Long id, String start, String end) {
        return new CalendarEventIndicatorResponse(id, CREATED_AT, LocalDate.parse(start), LocalDate.parse(end),
                CalendarEventColor.ORANGE);
    }
}
