package kr.ktb.zura.needu.calendar.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import kr.ktb.zura.needu.calendar.dto.response.CalendarEventDetailResponse;
import kr.ktb.zura.needu.calendar.dto.response.CalendarEventIndicatorResponse;
import kr.ktb.zura.needu.calendar.dto.response.DailyCalendarEventResponse;
import kr.ktb.zura.needu.calendar.entity.CalendarEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

    Optional<CalendarEvent> findByIdAndUserId(Long id, Long userId);

    @Query("""
            select new kr.ktb.zura.needu.calendar.dto.response.CalendarEventIndicatorResponse(
                e.id, e.createdAt ,e.startAt, e.endAt, e.color
            )
            from CalendarEvent e
            where e.userId = :userId
              and e.startAt < :nextMonthStart
              and e.endAt >= :monthStart
            order by e.createdAt desc, e.id desc
            """)
    List<CalendarEventIndicatorResponse> findMonthlyEventIndicators(
            @Param("userId") Long userId,
            @Param("monthStart") LocalDateTime monthStart,
            @Param("nextMonthStart") LocalDateTime nextMonthStart
    );

    @Query("""
            select new kr.ktb.zura.needu.calendar.dto.response.DailyCalendarEventResponse(
                e.id, e.title, e.startAt, e.endAt, e.createdAt, e.color
            )
            from CalendarEvent e
            where e.userId = :userId
              and e.startAt < :nextDayStart
              and e.endAt >= :dayStart
            order by e.createdAt DESC, e.id DESC
            """)
    List<DailyCalendarEventResponse> findAllDailyCalendarEvents(
            @Param("userId") Long userId,
            @Param("dayStart") LocalDateTime dayStart,
            @Param("nextDayStart") LocalDateTime nextDayStart
    );

    @Query("""
            select new kr.ktb.zura.needu.calendar.dto.response.CalendarEventDetailResponse(
                e.id, e.title, e.description, e.startAt, e.endAt, e.allDay, e.notificationEnabled, e.color
            )
            from CalendarEvent e
            where e.id = :eventId and e.userId = :userId
            """)
    Optional<CalendarEventDetailResponse> findCalendarEventDetail(
            @Param("userId") Long userId,
            @Param("eventId") Long eventId
    );
}
