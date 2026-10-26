package kr.ktb.zura.needu.calendar.repository;

import java.time.LocalDateTime;

import kr.ktb.zura.needu.calendar.entity.CalendarEvent;
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
}
