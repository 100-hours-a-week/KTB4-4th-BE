package kr.ktb.zura.needu.calendar.controller;

import jakarta.validation.constraints.Positive;
import kr.ktb.zura.needu.calendar.dto.response.CalendarEventDetailResponse;
import kr.ktb.zura.needu.common.response.ApiResponse;
import kr.ktb.zura.needu.calendar.service.CalendarEventCommandService;
import kr.ktb.zura.needu.calendar.service.CalendarEventQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
    public ResponseEntity<ApiResponse<CalendarEventDetailResponse>> findCalendarEvent(
            @AuthenticationPrincipal Long userId,
            @PathVariable @Positive Long eventId
    ) {
        return ResponseEntity.ok(ApiResponse.of(
                CalendarResponseMessages.CALENDAR_EVENT_FOUND,
                calendarEventQueryService.findCalendarEvent(userId, eventId)
        ));
    }

    @GetMapping
    public void findMonthlyCalendarEventSummary() {
    }

    @GetMapping("/daily")
    public void findAllDailyCalendarEvents() {
    }
}
