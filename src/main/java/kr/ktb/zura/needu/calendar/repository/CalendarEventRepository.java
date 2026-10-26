package kr.ktb.zura.needu.calendar.repository;

import java.time.LocalDateTime;
import java.util.List;
import kr.ktb.zura.needu.calendar.dto.response.DailyCalendarEventResponse;
import kr.ktb.zura.needu.calendar.entity.CalendarEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

    @Query("""
            select new kr.ktb.zura.needu.calendar.dto.response.DailyCalendarEventResponse(
                e.id, e.title, e.color
            )
            from CalendarEvent e
            where e.userId = :userId
              and e.startAt < :nextDayStart
              and e.endAt >= :dayStart
            order by e.startAt, e.id
            """)
    List<DailyCalendarEventResponse> findAllDailyCalendarEvents(
            @Param("userId") Long userId,
            @Param("dayStart") LocalDateTime dayStart,
            @Param("nextDayStart") LocalDateTime nextDayStart
    );
}
