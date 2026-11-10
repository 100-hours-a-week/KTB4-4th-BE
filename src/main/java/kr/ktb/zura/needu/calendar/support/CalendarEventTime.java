package kr.ktb.zura.needu.calendar.support;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

public final class CalendarEventTime {

    private static final ZoneId CALENDAR_ZONE = ZoneId.of("Asia/Seoul");

    private CalendarEventTime() {
    }

    public static LocalDateTime startOfDayUtc(LocalDate date) {
        return date.atStartOfDay(CALENDAR_ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    public static LocalDate toCalendarDate(LocalDateTime utcDateTime) {
        return utcDateTime.atOffset(ZoneOffset.UTC).atZoneSameInstant(CALENDAR_ZONE).toLocalDate();
    }

    public static OffsetDateTime toUtcOffsetDateTime(LocalDateTime utcDateTime) {
        return utcDateTime.atOffset(ZoneOffset.UTC);
    }
}
