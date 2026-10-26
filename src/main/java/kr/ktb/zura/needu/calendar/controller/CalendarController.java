package kr.ktb.zura.needu.calendar.controller;

import kr.ktb.zura.needu.calendar.service.CalendarEventCommandService;
import kr.ktb.zura.needu.calendar.service.CalendarEventQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
    public void findCalendarEvent() {
    }

    @GetMapping
    public void findMonthlyCalendarEventSummary() {
    }

    @GetMapping("/daily")
    public void findAllDailyCalendarEvents() {
    }
}
