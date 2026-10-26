package kr.ktb.zura.needu.calendar.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import kr.ktb.zura.needu.calendar.dto.response.CalendarEventIndicatorResponse;
import kr.ktb.zura.needu.calendar.dto.response.DailyCalendarEventResponse;
import kr.ktb.zura.needu.calendar.dto.response.MonthlyCalendarEventResponse;
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

    public List<MonthlyCalendarEventResponse> findMonthlyCalendarEventSummary(Long userId, YearMonth targetMonth) {
        userService.validateActiveUser(userId);

        LocalDate monthStart = targetMonth.atDay(1);
        LocalDate monthEnd = targetMonth.atEndOfMonth();
        List<CalendarEventIndicatorResponse> indicators = calendarEventRepository.findMonthlyEventIndicators(
                userId, CalendarEventTime.startOfDayUtc(monthStart), CalendarEventTime.startOfDayUtc(monthEnd.plusDays(1)));

        Map<LocalDate, List<CalendarEventIndicatorResponse>> indicatorsByDate = new TreeMap<>();
        for (CalendarEventIndicatorResponse indicator : indicators) {
            LocalDate start = indicator.startDate().isBefore(monthStart) ? monthStart : indicator.startDate();
            LocalDate end = indicator.endDate().isAfter(monthEnd) ? monthEnd : indicator.endDate();
            for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
                indicatorsByDate.computeIfAbsent(date, ignored -> new ArrayList<>()).add(indicator);
            }
        }

        return indicatorsByDate.entrySet().stream()
                .map(entry -> new MonthlyCalendarEventResponse(
                        entry.getKey(), entry.getValue().size(), entry.getValue()))
                .toList();
    }

    public List<DailyCalendarEventResponse> findAllDailyCalendarEvents(Long userId, LocalDate date) {
        userService.validateActiveUser(userId);

        return calendarEventRepository.findAllDailyCalendarEvents(
                userId, CalendarEventTime.startOfDayUtc(date), CalendarEventTime.startOfDayUtc(date.plusDays(1)));
    }
}
