package kr.ktb.zura.needu.calendar.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import kr.ktb.zura.needu.calendar.dto.response.CalendarEventDetailResponse;
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
    void seoulMidnightBoundaries_applyToDetailDates() {
        var event = saveEvent(1L, "2026-09-30T15:00", "2026-10-01T14:59:59");

        assertThat(calendarEventRepository.findCalendarEventDetail(1L, event.getId())).hasValueSatisfying(detail -> {
            assertThat(detail.startDate()).isEqualTo(LocalDate.of(2026, 10, 1));
            assertThat(detail.endDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        });
    }

    @Test
    void findCalendarEventDetail_projectsOwnedEventAndHidesOtherUsersEvent() {
        var event = saveEvent(1L, "2026-10-30T12:00", "2026-10-31T18:00");
        ReflectionTestUtils.setField(event, "description", "선물 구매");
        ReflectionTestUtils.setField(event, "allDay", true);
        calendarEventRepository.flush();

        assertThat(calendarEventRepository.findCalendarEventDetail(1L, event.getId()))
                .contains(new CalendarEventDetailResponse(event.getId(), "일정", "선물 구매",
                        LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 1), true, false, CalendarEventColor.ORANGE));
        assertThat(calendarEventRepository.findCalendarEventDetail(2L, event.getId())).isEmpty();
        assertThat(calendarEventRepository.findCalendarEventDetail(1L, Long.MAX_VALUE)).isEmpty();
    }

    private CalendarEvent saveEvent(Long userId, String start, String end) {
        CalendarEvent event = CalendarEvent.create(userId, "일정", null,
                LocalDateTime.parse(start), LocalDateTime.parse(end), false, false);
        ReflectionTestUtils.setField(event, "color", CalendarEventColor.ORANGE);
        return calendarEventRepository.saveAndFlush(event);
    }
}
