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
