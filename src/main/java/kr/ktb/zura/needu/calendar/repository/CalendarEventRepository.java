package kr.ktb.zura.needu.calendar.repository;

import java.util.Optional;
import kr.ktb.zura.needu.calendar.entity.CalendarEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

    Optional<CalendarEvent> findByIdAndUserId(Long id, Long userId);
}
