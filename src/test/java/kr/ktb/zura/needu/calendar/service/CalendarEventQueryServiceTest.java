package kr.ktb.zura.needu.calendar.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import kr.ktb.zura.needu.calendar.dto.response.DailyCalendarEventResponse;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CalendarEventQueryServiceTest {

    @Mock
    private CalendarEventRepository calendarEventRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private CalendarEventQueryService calendarEventQueryService;

    @Test
    void findAllDailyCalendarEvents_validatesUserBeforeQueryAndReturnsEvents() {
        LocalDate date = LocalDate.of(2026, 12, 31);
        LocalDateTime dayStart = LocalDateTime.of(2026, 12, 30, 15, 0);
        LocalDateTime nextDayStart = LocalDateTime.of(2026, 12, 31, 15, 0);
        var events = List.of(
                new DailyCalendarEventResponse(123L, "생일 선물 준비", CalendarEventColor.ORANGE),
                new DailyCalendarEventResponse(124L, "저녁 약속", CalendarEventColor.BLUE));
        given(calendarEventRepository.findAllDailyCalendarEvents(1L, dayStart, nextDayStart)).willReturn(events);

        assertThat(calendarEventQueryService.findAllDailyCalendarEvents(1L, date)).isSameAs(events);

        var order = inOrder(userService, calendarEventRepository);
        order.verify(userService).validateActiveUser(1L);
        order.verify(calendarEventRepository).findAllDailyCalendarEvents(1L, dayStart, nextDayStart);
    }

    @ParameterizedTest
    @EnumSource(value = UserErrorCode.class, names = {"USER_BLOCKED", "USER_ONBOARDING_REQUIRED", "USER_WITHDRAWN", "USER_NOT_FOUND"})
    void findAllDailyCalendarEvents_invalidUser_rejectsBeforeQueryingEvents(UserErrorCode error) {
        doThrow(new BusinessException(error)).when(userService).validateActiveUser(1L);

        assertThatThrownBy(() -> calendarEventQueryService.findAllDailyCalendarEvents(1L, LocalDate.of(2026, 10, 30)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(error));

        verifyNoInteractions(calendarEventRepository);
    }

    @Test
    void findAllDailyCalendarEvents_noEvents_returnsEmptyList() {
        given(calendarEventRepository.findAllDailyCalendarEvents(1L,
                LocalDateTime.of(2028, 2, 28, 15, 0), LocalDateTime.of(2028, 2, 29, 15, 0)))
                .willReturn(List.of());

        assertThat(calendarEventQueryService.findAllDailyCalendarEvents(1L, LocalDate.of(2028, 2, 29))).isEmpty();
        verify(userService).validateActiveUser(1L);
    }
}
