package kr.ktb.zura.needu.calendar.dto.response;

import java.time.LocalDate;
import java.util.List;

public record MonthlyCalendarEventResponse(
        LocalDate date,
        int eventCount,
        List<CalendarEventIndicatorResponse> indicators
) {
}
