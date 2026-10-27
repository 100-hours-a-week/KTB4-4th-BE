package kr.ktb.zura.needu.calendar.dto.response;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import kr.ktb.zura.needu.calendar.support.CalendarEventTime;
import kr.ktb.zura.needu.calendar.type.CalendarEventColor;

public record CalendarEventDetailResponse(
        Long eventId,
        String title,
        String description,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        boolean isAllDay,
        boolean isNotificationEnabled,
        CalendarEventColor color
) {
    public CalendarEventDetailResponse(Long eventId, String title, String description,
                                       LocalDateTime startAt, LocalDateTime endAt, boolean isAllDay,
                                       boolean isNotificationEnabled, CalendarEventColor color) {
        this(eventId, title, description, CalendarEventTime.toUtcOffsetDateTime(startAt),
                CalendarEventTime.toUtcOffsetDateTime(endAt), isAllDay, isNotificationEnabled, color);
    }
}
