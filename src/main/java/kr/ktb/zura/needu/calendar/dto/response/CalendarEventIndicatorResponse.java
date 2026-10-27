package kr.ktb.zura.needu.calendar.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import kr.ktb.zura.needu.calendar.support.CalendarEventTime;
import kr.ktb.zura.needu.calendar.type.CalendarEventColor;

public record CalendarEventIndicatorResponse(
        Long eventId,
        LocalDateTime createdAt,
        LocalDate startDate,
        LocalDate endDate,
        CalendarEventColor color
) {
    public CalendarEventIndicatorResponse(Long eventId, LocalDateTime createdAt, LocalDateTime startAt,
                                          LocalDateTime endAt, CalendarEventColor color) {
        this(eventId, createdAt, CalendarEventTime.toCalendarDate(startAt), CalendarEventTime.toCalendarDate(endAt),
                color);
    }
}
