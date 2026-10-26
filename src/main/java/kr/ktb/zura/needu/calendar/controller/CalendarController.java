package kr.ktb.zura.needu.calendar.controller;

import java.time.YearMonth;
import kr.ktb.zura.needu.calendar.dto.response.MonthlyCalendarEventResponse;
import kr.ktb.zura.needu.common.response.ApiResponse;
import kr.ktb.zura.needu.common.response.ItemsResponse;
import kr.ktb.zura.needu.calendar.service.CalendarEventCommandService;
import kr.ktb.zura.needu.calendar.service.CalendarEventQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/calendar/events")
public class CalendarController {

    private final CalendarEventCommandService calendarEventCommandService;
    private final CalendarEventQueryService calendarEventQueryService;

    @PostMapping
    public void createCalendarEvent() {
    }

    @PatchMapping("/{eventId}")
    public void updateCalendarEvent() {
    }

    @DeleteMapping("/{eventId}")
    public void deleteCalendarEvent() {
    }

    @GetMapping("/{eventId}")
    public void findCalendarEvent() {
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ItemsResponse<MonthlyCalendarEventResponse>>> findMonthlyCalendarEventSummary(
            @AuthenticationPrincipal Long userId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth targetMonth
    ) {
        return ResponseEntity.ok(ApiResponse.of(
                CalendarResponseMessages.CALENDAR_EVENT_SUMMARY_FOUND,
                new ItemsResponse<>(calendarEventQueryService.findMonthlyCalendarEventSummary(userId, targetMonth))
        ));
    }

    @GetMapping("/daily")
    public void findAllDailyCalendarEvents() {
    }
}
