package kr.ktb.zura.needu.calendar.dto.response;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import kr.ktb.zura.needu.calendar.support.CalendarEventTime;
import kr.ktb.zura.needu.calendar.type.CalendarEventColor;

public record DailyCalendarEventResponse(
        Long eventId,
        String title,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        OffsetDateTime createdAt,
        CalendarEventColor color
) {
    public DailyCalendarEventResponse(Long eventId, String title, LocalDateTime startAt, LocalDateTime endAt,
                                      LocalDateTime createdAt, CalendarEventColor color) {
        this(eventId, title, CalendarEventTime.toUtcOffsetDateTime(startAt),
                CalendarEventTime.toUtcOffsetDateTime(endAt), CalendarEventTime.toUtcOffsetDateTime(createdAt), color);
    }
}
