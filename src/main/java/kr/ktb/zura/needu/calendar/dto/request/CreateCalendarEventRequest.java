package kr.ktb.zura.needu.calendar.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import kr.ktb.zura.needu.calendar.entity.CalendarEvent;
import kr.ktb.zura.needu.calendar.type.CalendarEventColor;

public record CreateCalendarEventRequest(
        @NotBlank @Size(max = CalendarEvent.TITLE_MAX_LENGTH) String title,
        @NotNull OffsetDateTime startAt,
        @NotNull OffsetDateTime endAt,
        @NotNull Boolean allDay,
        String description,
        CalendarEventColor color,
        Boolean notificationEnabled
) {
}
