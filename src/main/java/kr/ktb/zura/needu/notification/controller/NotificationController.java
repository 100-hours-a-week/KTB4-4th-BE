package kr.ktb.zura.needu.notification.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDateTime;

import kr.ktb.zura.needu.common.response.ApiResponse;
import kr.ktb.zura.needu.notification.dto.request.NotificationSearchCondition;
import kr.ktb.zura.needu.notification.dto.response.NotificationListApiResponse;
import kr.ktb.zura.needu.notification.dto.response.NotificationReadResponse;
import kr.ktb.zura.needu.notification.dto.response.NotificationSummaryResponse;
import kr.ktb.zura.needu.notification.service.NotificationService;
import kr.ktb.zura.needu.notification.service.NotificationStreamService;
import kr.ktb.zura.needu.notification.type.NotificationCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private static final String ALL_CATEGORY = "ALL";

    private final NotificationService notificationService;
    private final NotificationStreamService notificationStreamService;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<NotificationSummaryResponse>> findNotificationSummary(
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.of(
                NotificationResponseMessages.NOTIFICATION_SUMMARY_FOUND,
                notificationService.findNotificationSummary(userId)
        ));
    }

    @GetMapping
    public ResponseEntity<NotificationListApiResponse> findAllNotifications(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = ALL_CATEGORY) @Pattern(regexp = "ALL|CHAT|POKE|EVENT|FRIEND_JOINED")
            String category,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = NotificationPageLimits.DEFAULT_PAGE_SIZE)
            @Min(NotificationPageLimits.MIN_PAGE_SIZE) @Max(NotificationPageLimits.MAX_PAGE_SIZE) int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since
    ) {
        NotificationCategory categoryFilter =
                ALL_CATEGORY.equals(category) ? null : NotificationCategory.valueOf(category);
        NotificationSearchCondition condition = new NotificationSearchCondition(categoryFilter, cursor, size, since);
        return ResponseEntity.ok(NotificationListApiResponse.of(
                NotificationResponseMessages.NOTIFICATIONS_FOUND,
                notificationService.findAllNotifications(userId, condition)
        ));
    }

    @PatchMapping("/{notificationId}")
    public ResponseEntity<ApiResponse<NotificationReadResponse>> readNotification(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long notificationId
    ) {
        return ResponseEntity.ok(ApiResponse.of(
                NotificationResponseMessages.NOTIFICATION_READ,
                notificationService.readNotification(userId, notificationId)
        ));
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Void> deleteNotification(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long notificationId
    ) {
        notificationService.deleteNotification(userId, notificationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> connectStream(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(notificationStreamService.connect(userId));
    }
}
