package kr.ktb.zura.needu.calendar.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import kr.ktb.zura.needu.calendar.entity.CalendarEvent;
import kr.ktb.zura.needu.calendar.type.CalendarEventColor;

public record UpdateCalendarEventRequest(
        @Size(max = CalendarEvent.TITLE_MAX_LENGTH)
        @Pattern(regexp = UpdateCalendarEventRequest.NOT_BLANK_PATTERN)
        String title,
        String description,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        Boolean allDay,
        CalendarEventColor color,
        Boolean notificationEnabled
) {

    private static final String NOT_BLANK_PATTERN = "(?s).*\\S.*";

    @AssertTrue
    public boolean isAnyFieldPresent() {
        return title != null || description != null || startAt != null || endAt != null
                || allDay != null || color != null || notificationEnabled != null;
    }

    @AssertTrue
    public boolean isPeriodPairPresent() {
        return (startAt == null) == (endAt == null);
    }

    public boolean hasPeriod() {
        return startAt != null;
    }
}
