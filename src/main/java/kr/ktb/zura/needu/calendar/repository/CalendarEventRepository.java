package kr.ktb.zura.needu.calendar.repository;

import java.util.Optional;
import kr.ktb.zura.needu.calendar.dto.response.CalendarEventDetailResponse;
import kr.ktb.zura.needu.calendar.entity.CalendarEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

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
