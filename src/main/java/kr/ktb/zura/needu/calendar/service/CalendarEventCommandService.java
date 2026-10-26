package kr.ktb.zura.needu.calendar.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import kr.ktb.zura.needu.calendar.dto.request.CreateCalendarEventRequest;
import kr.ktb.zura.needu.calendar.dto.response.CalendarEventResponse;
import kr.ktb.zura.needu.calendar.entity.CalendarEvent;
import kr.ktb.zura.needu.calendar.repository.CalendarEventRepository;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarEventCommandService {

    private final CalendarEventRepository calendarEventRepository;
    private final UserService userService;

    @Transactional
    public CalendarEventResponse createCalendarEvent(Long userId, CreateCalendarEventRequest request) {
        userService.validateActiveUser(userId);
        validateEventPeriod(request.startAt(), request.endAt());

        CalendarEvent calendarEvent = calendarEventRepository.save(toCalendarEvent(userId, request));

        log.info("Calendar event created. userId={}, eventId={}", userId, calendarEvent.getId());
        return CalendarEventResponse.from(calendarEvent);
    }

    private void validateEventPeriod(OffsetDateTime startAt, OffsetDateTime endAt) {
        if (endAt.isBefore(startAt)) {
            throw new BusinessException(CommonErrorCode.COMMON_INVALID_INPUT);
        }
    }

    private CalendarEvent toCalendarEvent(Long userId, CreateCalendarEventRequest request) {
        return CalendarEvent.create(
                userId,
                request.title(),
                request.description(),
                toUtc(request.startAt()),
                toUtc(request.endAt()),
                request.allDay(),
                request.color(),
                Boolean.TRUE.equals(request.notificationEnabled())
        );
    }

    private LocalDateTime toUtc(OffsetDateTime dateTime) {
        return dateTime.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
}
