package kr.ktb.zura.needu.calendar.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import kr.ktb.zura.needu.calendar.dto.response.CalendarEventIndicatorResponse;
import kr.ktb.zura.needu.calendar.entity.CalendarEvent;
import kr.ktb.zura.needu.calendar.type.CalendarEventColor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DataJpaTest
class CalendarEventRepositoryTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    @Autowired
    private CalendarEventRepository calendarEventRepository;

    @Test
    void ownEvent_findByIdAndUserId_returnsEvent() {
        CalendarEvent saved = calendarEventRepository.saveAndFlush(createCalendarEvent(USER_ID));

        assertThat(calendarEventRepository.findByIdAndUserId(saved.getId(), USER_ID))
                .hasValueSatisfying(event -> assertThat(event.getId()).isEqualTo(saved.getId()));
    }

    @Test
    void otherUsersEvent_findByIdAndUserId_returnsEmpty() {
        CalendarEvent saved = calendarEventRepository.saveAndFlush(createCalendarEvent(OTHER_USER_ID));

        assertThat(calendarEventRepository.findByIdAndUserId(saved.getId(), USER_ID)).isEmpty();
    }

    @Test
    void seoulMidnightBoundaries_applyToMonthlyIndicators() {
        var event = saveEvent(1L, "2026-09-30T15:00", "2026-10-01T14:59:59");
        saveEvent(1L, "2026-09-30T14:00", "2026-09-30T14:59:59");
        var nextDay = saveEvent(1L, "2026-10-01T15:00", "2026-10-01T16:00");
        saveEvent(1L, "2026-10-31T15:00", "2026-10-31T16:00");

        var monthly = calendarEventRepository.findMonthlyEventIndicators(1L,
                LocalDateTime.parse("2026-09-30T15:00"), LocalDateTime.parse("2026-10-31T15:00"));
        assertThat(monthly).extracting(CalendarEventIndicatorResponse::eventId)
                .containsExactly(nextDay.getId(), event.getId());
        assertThat(monthly.getLast().startDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(monthly.getLast().endDate()).isEqualTo(LocalDate.of(2026, 10, 1));
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

        // 최근에 등록한 일정부터 내려준다
        assertThat(result).extracting(CalendarEventIndicatorResponse::eventId)
                .containsExactly(sameStart.getId(), spanning.getId(), crossingStart.getId(), later.getId());
        // DB에 저장된 createdAt은 마이크로초까지만 남으므로 따로 오차를 두고 비교한다
        assertThat(result.get(2)).usingRecursiveComparison().ignoringFields("createdAt")
                .isEqualTo(new CalendarEventIndicatorResponse(crossingStart.getId(), null,
                        LocalDate.parse("2026-09-30"), LocalDate.parse("2026-10-01"), CalendarEventColor.ORANGE));
        assertThat(result.get(2).createdAt()).isCloseTo(crossingStart.getCreatedAt(), within(1, ChronoUnit.MILLIS));
    }

    private CalendarEvent createCalendarEvent(Long userId) {
        return CalendarEvent.create(
                userId,
                "생일 선물 준비",
                null,
                LocalDateTime.of(2026, 10, 30, 10, 0),
                LocalDateTime.of(2026, 10, 30, 11, 0),
                false,
                null,
                false
        );
    }

    private CalendarEvent saveEvent(Long userId, String start, String end) {
        return calendarEventRepository.saveAndFlush(CalendarEvent.create(userId, "일정", null,
                LocalDateTime.parse(start), LocalDateTime.parse(end), false, CalendarEventColor.ORANGE, false));
    }
}
