package kr.ktb.zura.needu.calendar.dto.response;

import kr.ktb.zura.needu.calendar.entity.CalendarEvent;

public record CalendarEventResponse(Long eventId) {

    public static CalendarEventResponse from(CalendarEvent calendarEvent) {
        return new CalendarEventResponse(calendarEvent.getId());
    }
}
