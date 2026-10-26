package kr.ktb.zura.needu.calendar.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import kr.ktb.zura.needu.calendar.dto.response.CalendarEventIndicatorResponse;
import kr.ktb.zura.needu.calendar.entity.CalendarEvent;
import kr.ktb.zura.needu.calendar.type.CalendarEventColor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class CalendarEventRepositoryTest {

    @Autowired
    private CalendarEventRepository calendarEventRepository;

    @Test
    void seoulMidnightBoundaries_applyToMonthlyIndicators() {
        var event = saveEvent(1L, "2026-09-30T15:00", "2026-10-01T14:59:59");
        saveEvent(1L, "2026-09-30T14:00", "2026-09-30T14:59:59");
        var nextDay = saveEvent(1L, "2026-10-01T15:00", "2026-10-01T16:00");
        saveEvent(1L, "2026-10-31T15:00", "2026-10-31T16:00");

        var monthly = calendarEventRepository.findMonthlyEventIndicators(1L,
                LocalDateTime.parse("2026-09-30T15:00"), LocalDateTime.parse("2026-10-31T15:00"));
        assertThat(monthly).extracting(CalendarEventIndicatorResponse::eventId)
                .containsExactly(event.getId(), nextDay.getId());
        assertThat(monthly.getFirst().startDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(monthly.getFirst().endDate()).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    void findMonthlyEventIndicators_projectsOnlyOwnedOverlappingEventsInOrder() {
        var later = saveEvent(1L, "2026-10-31T12:00", "2026-11-01T00:00");
        var crossingStart = saveEvent(1L, "2026-09-30T12:00", "2026-10-01T00:00");
        var spanning = saveEvent(1L, "2026-09-01T00:00", "2026-11-30T00:00");
        var sameStart = saveEvent(1L, "2026-10-31T12:00", "2026-10-31T13:00");
        saveEvent(2L, "2026-10-15T12:00", "2026-10-15T13:00");
        saveEvent(1L, "2026-09-30T12:00", "2026-09-30T23:59:59");
        saveEvent(1L, "2026-11-01T00:00", "2026-11-01T12:00");

        var result = calendarEventRepository.findMonthlyEventIndicators(1L,
                LocalDateTime.parse("2026-10-01T00:00"), LocalDateTime.parse("2026-11-01T00:00"));

        assertThat(result).extracting(CalendarEventIndicatorResponse::eventId)
                .containsExactly(spanning.getId(), crossingStart.getId(), later.getId(), sameStart.getId());
        assertThat(result.get(1)).isEqualTo(new CalendarEventIndicatorResponse(crossingStart.getId(),
                LocalDate.parse("2026-09-30"), LocalDate.parse("2026-10-01"), CalendarEventColor.ORANGE));
    }

    private CalendarEvent saveEvent(Long userId, String start, String end) {
        CalendarEvent event = CalendarEvent.create(userId, "일정", null,
                LocalDateTime.parse(start), LocalDateTime.parse(end), false, false);
        ReflectionTestUtils.setField(event, "color", CalendarEventColor.ORANGE);
        return calendarEventRepository.saveAndFlush(event);
    }
}
