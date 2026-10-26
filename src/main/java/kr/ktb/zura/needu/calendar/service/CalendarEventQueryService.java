package kr.ktb.zura.needu.calendar.service;

import java.time.LocalDate;
import java.util.List;
import kr.ktb.zura.needu.calendar.dto.response.DailyCalendarEventResponse;
import kr.ktb.zura.needu.calendar.repository.CalendarEventRepository;
import kr.ktb.zura.needu.calendar.support.CalendarEventTime;
import kr.ktb.zura.needu.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarEventQueryService {

    private final CalendarEventRepository calendarEventRepository;
    private final UserService userService;

    public List<DailyCalendarEventResponse> findAllDailyCalendarEvents(Long userId, LocalDate date) {
        userService.validateActiveUser(userId);

        return calendarEventRepository.findAllDailyCalendarEvents(
                userId, CalendarEventTime.startOfDayUtc(date), CalendarEventTime.startOfDayUtc(date.plusDays(1)));
    }
}
