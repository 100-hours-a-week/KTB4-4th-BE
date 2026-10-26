package kr.ktb.zura.needu.calendar.service;

import java.time.LocalDate;
import java.util.Optional;
import kr.ktb.zura.needu.calendar.dto.response.CalendarEventDetailResponse;
import kr.ktb.zura.needu.calendar.exception.CalendarErrorCode;
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

    @Mock
    private CalendarEventRepository calendarEventRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private CalendarEventQueryService calendarEventQueryService;

    @Test
    void findCalendarEvent_validatesUserAndReturnsDetailProjection() {
        var detail = new CalendarEventDetailResponse(123L, "일정", null,
                LocalDate.of(2026, 10, 30), LocalDate.of(2026, 10, 31), false, true, CalendarEventColor.ORANGE);
        given(calendarEventRepository.findCalendarEventDetail(1L, 123L)).willReturn(Optional.of(detail));

        assertThat(calendarEventQueryService.findCalendarEvent(1L, 123L)).isSameAs(detail);
        verify(userService).validateActiveUser(1L);
    }

    @Test
    void findCalendarEvent_missingOrNotOwned_throwsNotFound() {
        given(calendarEventRepository.findCalendarEventDetail(1L, 123L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> calendarEventQueryService.findCalendarEvent(1L, 123L))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(CalendarErrorCode.CALENDAR_EVENT_NOT_FOUND));
    }

    @ParameterizedTest
    @EnumSource(value = UserErrorCode.class, names = {"USER_BLOCKED", "USER_ONBOARDING_REQUIRED", "USER_WITHDRAWN", "USER_NOT_FOUND"})
    void findCalendarEvent_invalidUser_rejectsBeforeQueryingEvents(UserErrorCode error) {
        doThrow(new BusinessException(error)).when(userService).validateActiveUser(1L);

        assertThatThrownBy(() -> calendarEventQueryService.findCalendarEvent(1L, 123L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(error));

        verifyNoInteractions(calendarEventRepository);
    }
}
