package kr.ktb.zura.needu.calendar.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import kr.ktb.zura.needu.calendar.support.CalendarEventTime;
import kr.ktb.zura.needu.calendar.type.CalendarEventColor;

public record CalendarEventDetailResponse(
        Long eventId,
        String title,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        boolean isAllDay,
        boolean isNotificationEnabled,
        CalendarEventColor color
) {
    public CalendarEventDetailResponse(Long eventId, String title, String description,
                                       LocalDateTime startAt, LocalDateTime endAt, boolean isAllDay,
                                       boolean isNotificationEnabled, CalendarEventColor color) {
        this(eventId, title, description, CalendarEventTime.toCalendarDate(startAt),
                CalendarEventTime.toCalendarDate(endAt), isAllDay, isNotificationEnabled, color);
    }
}
