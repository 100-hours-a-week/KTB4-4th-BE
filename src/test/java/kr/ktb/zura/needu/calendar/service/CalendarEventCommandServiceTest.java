package kr.ktb.zura.needu.calendar.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;

import kr.ktb.zura.needu.calendar.dto.request.CreateCalendarEventRequest;
import kr.ktb.zura.needu.calendar.dto.request.UpdateCalendarEventRequest;
import kr.ktb.zura.needu.calendar.dto.response.CalendarEventResponse;
import kr.ktb.zura.needu.calendar.entity.CalendarEvent;
import kr.ktb.zura.needu.calendar.exception.CalendarErrorCode;
import kr.ktb.zura.needu.calendar.repository.CalendarEventRepository;
import kr.ktb.zura.needu.calendar.type.CalendarEventColor;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import kr.ktb.zura.needu.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CalendarEventCommandServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long EVENT_ID = 101L;
    private static final OffsetDateTime START_AT = OffsetDateTime.parse("2026-10-30T19:00:00+09:00");
    private static final OffsetDateTime END_AT = OffsetDateTime.parse("2026-10-30T20:00:00+09:00");

    @Mock
    private CalendarEventRepository calendarEventRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private CalendarEventCommandService calendarEventCommandService;

    @Test
    void validRequest_createCalendarEvent_savesEventInUtcAndReturnsId() {
        givenSavedWithId(101L);
        CreateCalendarEventRequest request = new CreateCalendarEventRequest(
                "생일 선물 준비", START_AT, END_AT, false, "선물 구매", CalendarEventColor.RED, true);

        CalendarEventResponse response = calendarEventCommandService.createCalendarEvent(USER_ID, request);

        verify(userService).validateActiveUser(USER_ID);
        assertThat(response.eventId()).isEqualTo(101L);
        CalendarEvent saved = captureSavedEvent();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getTitle()).isEqualTo("생일 선물 준비");
        assertThat(saved.getDescription()).isEqualTo("선물 구매");
        assertThat(saved.getStartAt()).isEqualTo(LocalDateTime.of(2026, 10, 30, 10, 0));
        assertThat(saved.getEndAt()).isEqualTo(LocalDateTime.of(2026, 10, 30, 11, 0));
        assertThat(saved.isAllDay()).isFalse();
        assertThat(saved.getColor()).isEqualTo(CalendarEventColor.RED);
        assertThat(saved.isNotificationEnabled()).isTrue();
    }

    @Test
    void optionalFieldsOmitted_createCalendarEvent_savesWithoutColorAndNotification() {
        givenSavedWithId(101L);
        CreateCalendarEventRequest request = new CreateCalendarEventRequest(
                "생일 선물 준비", START_AT, END_AT, true, null, null, null);

        calendarEventCommandService.createCalendarEvent(USER_ID, request);

        CalendarEvent saved = captureSavedEvent();
        assertThat(saved.getDescription()).isNull();
        assertThat(saved.getColor()).isNull();
        assertThat(saved.isNotificationEnabled()).isFalse();
        assertThat(saved.isAllDay()).isTrue();
    }

    @Test
    void sameStartAndEnd_createCalendarEvent_savesEvent() {
        givenSavedWithId(101L);
        CreateCalendarEventRequest request = new CreateCalendarEventRequest(
                "생일 선물 준비", START_AT, START_AT, false, null, null, null);

        CalendarEventResponse response = calendarEventCommandService.createCalendarEvent(USER_ID, request);

        assertThat(response.eventId()).isEqualTo(101L);
    }

    @Test
    void endBeforeStart_createCalendarEvent_throwsInvalidInputWithoutSaving() {
        CreateCalendarEventRequest request = new CreateCalendarEventRequest(
                "생일 선물 준비", END_AT, START_AT, false, null, null, null);

        assertThatThrownBy(() -> calendarEventCommandService.createCalendarEvent(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.COMMON_INVALID_INPUT);

        verifyNoInteractions(calendarEventRepository);
    }

    @Test
    void inactiveUser_createCalendarEvent_throwsWithoutSaving() {
        willThrow(new BusinessException(UserErrorCode.USER_ONBOARDING_REQUIRED))
                .given(userService).validateActiveUser(USER_ID);
        CreateCalendarEventRequest request = new CreateCalendarEventRequest(
                "생일 선물 준비", START_AT, END_AT, false, null, null, null);

        assertThatThrownBy(() -> calendarEventCommandService.createCalendarEvent(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_ONBOARDING_REQUIRED);

        verifyNoInteractions(calendarEventRepository);
    }

    @Test
    void allFieldsSent_updateCalendarEvent_changesAllFieldsInUtc() {
        CalendarEvent calendarEvent = givenOwnedEvent();
        UpdateCalendarEventRequest request = new UpdateCalendarEventRequest(
                "생일 선물 구매",
                "백화점에서 향수 구매",
                OffsetDateTime.parse("2026-10-30T20:00:00+09:00"),
                OffsetDateTime.parse("2026-10-30T21:00:00+09:00"),
                true,
                CalendarEventColor.RED,
                true
        );

        CalendarEventResponse response = calendarEventCommandService.updateCalendarEvent(USER_ID, EVENT_ID, request);

        verify(userService).validateActiveUser(USER_ID);
        assertThat(response.eventId()).isEqualTo(EVENT_ID);
        assertThat(calendarEvent.getTitle()).isEqualTo("생일 선물 구매");
        assertThat(calendarEvent.getDescription()).isEqualTo("백화점에서 향수 구매");
        assertThat(calendarEvent.getStartAt()).isEqualTo(LocalDateTime.of(2026, 10, 30, 11, 0));
        assertThat(calendarEvent.getEndAt()).isEqualTo(LocalDateTime.of(2026, 10, 30, 12, 0));
        assertThat(calendarEvent.isAllDay()).isTrue();
        assertThat(calendarEvent.getColor()).isEqualTo(CalendarEventColor.RED);
        assertThat(calendarEvent.isNotificationEnabled()).isTrue();
    }

    @Test
    void onlyTitleSent_updateCalendarEvent_keepsOtherFields() {
        CalendarEvent calendarEvent = givenOwnedEvent();
        UpdateCalendarEventRequest request = titleOnlyUpdateRequest();

        calendarEventCommandService.updateCalendarEvent(USER_ID, EVENT_ID, request);

        assertThat(calendarEvent.getTitle()).isEqualTo("생일 선물 구매");
        assertThat(calendarEvent.getDescription()).isEqualTo("선물 구매");
        assertThat(calendarEvent.getStartAt()).isEqualTo(LocalDateTime.of(2026, 10, 30, 10, 0));
        assertThat(calendarEvent.getEndAt()).isEqualTo(LocalDateTime.of(2026, 10, 30, 11, 0));
        assertThat(calendarEvent.isAllDay()).isFalse();
        assertThat(calendarEvent.getColor()).isEqualTo(CalendarEventColor.BLUE);
        assertThat(calendarEvent.isNotificationEnabled()).isFalse();
    }

    @Test
    void onlyNotificationEnabledSent_updateCalendarEvent_changesOnlyNotificationEnabled() {
        CalendarEvent calendarEvent = givenOwnedEvent();
        UpdateCalendarEventRequest request = updateRequestWithNotificationEnabled(true);

        calendarEventCommandService.updateCalendarEvent(USER_ID, EVENT_ID, request);

        assertThat(calendarEvent.isNotificationEnabled()).isTrue();
        assertThat(calendarEvent.getTitle()).isEqualTo("생일 선물 준비");
        assertThat(calendarEvent.isAllDay()).isFalse();
    }

    @Test
    void endBeforeStart_updateCalendarEvent_throwsInvalidInputWithoutQuerying() {
        UpdateCalendarEventRequest request = new UpdateCalendarEventRequest(null, null, END_AT, START_AT, null, null, null);

        assertThatThrownBy(() -> calendarEventCommandService.updateCalendarEvent(USER_ID, EVENT_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.COMMON_INVALID_INPUT);

        verifyNoInteractions(calendarEventRepository);
    }

    @Test
    void eventNotOwnedOrMissing_updateCalendarEvent_throwsNotFound() {
        given(calendarEventRepository.findByIdAndUserId(EVENT_ID, USER_ID)).willReturn(Optional.empty());
        UpdateCalendarEventRequest request = titleOnlyUpdateRequest();

        assertThatThrownBy(() -> calendarEventCommandService.updateCalendarEvent(USER_ID, EVENT_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CalendarErrorCode.CALENDAR_EVENT_NOT_FOUND);
    }

    @Test
    void inactiveUser_updateCalendarEvent_throwsWithoutQuerying() {
        willThrow(new BusinessException(UserErrorCode.USER_BLOCKED))
                .given(userService).validateActiveUser(USER_ID);
        UpdateCalendarEventRequest request = titleOnlyUpdateRequest();

        assertThatThrownBy(() -> calendarEventCommandService.updateCalendarEvent(USER_ID, EVENT_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_BLOCKED);

        verifyNoInteractions(calendarEventRepository);
    }

    private UpdateCalendarEventRequest titleOnlyUpdateRequest() {
        return new UpdateCalendarEventRequest("생일 선물 구매", null, null, null, null, null, null);
    }

    private UpdateCalendarEventRequest updateRequestWithNotificationEnabled(boolean notificationEnabled) {
        return new UpdateCalendarEventRequest(null, null, null, null, null, null, notificationEnabled);
    }

    private CalendarEvent givenOwnedEvent() {
        CalendarEvent calendarEvent = CalendarEvent.create(
                USER_ID,
                "생일 선물 준비",
                "선물 구매",
                LocalDateTime.of(2026, 10, 30, 10, 0),
                LocalDateTime.of(2026, 10, 30, 11, 0),
                false,
                CalendarEventColor.BLUE,
                false
        );
        ReflectionTestUtils.setField(calendarEvent, "id", EVENT_ID);
        given(calendarEventRepository.findByIdAndUserId(EVENT_ID, USER_ID)).willReturn(Optional.of(calendarEvent));
        return calendarEvent;
    }

    private void givenSavedWithId(Long eventId) {
        given(calendarEventRepository.save(any(CalendarEvent.class))).willAnswer(invocation -> {
            CalendarEvent calendarEvent = invocation.getArgument(0);
            // ID는 DB에서 생성되므로 단위 테스트에서만 직접 설정한다.
            ReflectionTestUtils.setField(calendarEvent, "id", eventId);
            return calendarEvent;
        });
    }

    private CalendarEvent captureSavedEvent() {
        ArgumentCaptor<CalendarEvent> captor = ArgumentCaptor.forClass(CalendarEvent.class);
        verify(calendarEventRepository).save(captor.capture());
        return captor.getValue();
    }
}
