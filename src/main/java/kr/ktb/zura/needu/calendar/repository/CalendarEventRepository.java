package kr.ktb.zura.needu.calendar.repository;

import java.time.LocalDateTime;
import java.util.List;
import kr.ktb.zura.needu.calendar.dto.response.CalendarEventIndicatorResponse;
import kr.ktb.zura.needu.calendar.entity.CalendarEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

    @Query("""
            select new kr.ktb.zura.needu.calendar.dto.response.CalendarEventIndicatorResponse(
                e.id, e.startAt, e.endAt, e.color
            )
            from CalendarEvent e
            where e.userId = :userId
              and e.startAt < :nextMonthStart
              and e.endAt >= :monthStart
            order by e.startAt, e.id
            """)
    List<CalendarEventIndicatorResponse> findMonthlyEventIndicators(
            @Param("userId") Long userId,
            @Param("monthStart") LocalDateTime monthStart,
            @Param("nextMonthStart") LocalDateTime nextMonthStart
    );
}
