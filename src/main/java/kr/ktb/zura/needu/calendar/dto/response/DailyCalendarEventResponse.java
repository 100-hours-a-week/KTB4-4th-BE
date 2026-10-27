package kr.ktb.zura.needu.calendar.dto.response;

import kr.ktb.zura.needu.calendar.type.CalendarEventColor;

public record DailyCalendarEventResponse(
        Long eventId,
        String title,
        CalendarEventColor color
) {
}
