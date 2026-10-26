package kr.ktb.zura.needu.calendar.repository;

import java.time.LocalDateTime;

import kr.ktb.zura.needu.calendar.dto.response.DailyCalendarEventResponse;
import kr.ktb.zura.needu.calendar.entity.CalendarEvent;
import kr.ktb.zura.needu.calendar.type.CalendarEventColor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

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
    void seoulMidnightBoundaries_applyToDailyEvents() {
        var event = saveEvent(1L, "2026-09-30T15:00", "2026-10-01T14:59:59");
        saveEvent(1L, "2026-09-30T14:00", "2026-09-30T14:59:59");
        saveEvent(1L, "2026-10-01T15:00", "2026-10-01T16:00");

        assertThat(calendarEventRepository.findAllDailyCalendarEvents(1L,
                LocalDateTime.parse("2026-09-30T15:00"), LocalDateTime.parse("2026-10-01T15:00")))
                .extracting(DailyCalendarEventResponse::eventId).containsExactly(event.getId());
    }

    @Test
    void findAllDailyCalendarEvents_projectsOnlyOwnedOverlappingEventsInOrder() {
        var later = saveEvent(1L, "2026-10-30T23:59:59", "2026-10-31T12:00");
        var crossingStart = saveEvent(1L, "2026-10-29T12:00", "2026-10-30T00:00");
        var spanning = saveEvent(1L, "2026-10-28T00:00", "2026-11-01T00:00");
        var sameStart = saveEvent(1L, "2026-10-30T23:59:59", "2026-10-31T13:00");
        saveEvent(2L, "2026-10-30T12:00", "2026-10-30T13:00");
        saveEvent(1L, "2026-10-29T12:00", "2026-10-29T23:59:59");
        saveEvent(1L, "2026-10-31T00:00", "2026-10-31T12:00");

        var result = calendarEventRepository.findAllDailyCalendarEvents(1L,
                LocalDateTime.parse("2026-10-30T00:00"), LocalDateTime.parse("2026-10-31T00:00"));

        assertThat(result).containsExactly(
                new DailyCalendarEventResponse(spanning.getId(), "일정", CalendarEventColor.ORANGE),
                new DailyCalendarEventResponse(crossingStart.getId(), "일정", CalendarEventColor.ORANGE),
                new DailyCalendarEventResponse(later.getId(), "일정", CalendarEventColor.ORANGE),
                new DailyCalendarEventResponse(sameStart.getId(), "일정", CalendarEventColor.ORANGE));
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
