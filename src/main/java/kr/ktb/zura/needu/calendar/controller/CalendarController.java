package kr.ktb.zura.needu.calendar.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import kr.ktb.zura.needu.calendar.dto.request.CreateCalendarEventRequest;
import kr.ktb.zura.needu.calendar.dto.request.UpdateCalendarEventRequest;
import kr.ktb.zura.needu.calendar.dto.response.CalendarEventDetailResponse;
import kr.ktb.zura.needu.calendar.dto.response.CalendarEventResponse;
import kr.ktb.zura.needu.calendar.service.CalendarEventCommandService;
import kr.ktb.zura.needu.calendar.service.CalendarEventQueryService;
import kr.ktb.zura.needu.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/calendar/events")
public class CalendarController {

    private final CalendarEventCommandService calendarEventCommandService;
    private final CalendarEventQueryService calendarEventQueryService;

    @PostMapping
    public ResponseEntity<ApiResponse<CalendarEventResponse>> createCalendarEvent(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid CreateCalendarEventRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(
                CalendarResponseMessages.CALENDAR_EVENT_CREATED,
                calendarEventCommandService.createCalendarEvent(userId, request)
        ));
    }

    @PatchMapping("/{eventId}")
    public ResponseEntity<ApiResponse<CalendarEventResponse>> updateCalendarEvent(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long eventId,
            @RequestBody @Valid UpdateCalendarEventRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.of(
                CalendarResponseMessages.CALENDAR_EVENT_UPDATED,
                calendarEventCommandService.updateCalendarEvent(userId, eventId, request)
        ));
    }

    @DeleteMapping("/{eventId}")
    public ResponseEntity<Void> deleteCalendarEvent(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long eventId
    ) {
        calendarEventCommandService.deleteCalendarEvent(userId, eventId);
        return ResponseEntity.noContent().build();
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
