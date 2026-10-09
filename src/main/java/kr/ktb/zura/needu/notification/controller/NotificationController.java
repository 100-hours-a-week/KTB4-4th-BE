package kr.ktb.zura.needu.notification.controller;

import jakarta.validation.Valid;

import kr.ktb.zura.needu.common.response.ApiResponse;
import kr.ktb.zura.needu.notification.dto.request.NotificationListRequest;
import kr.ktb.zura.needu.notification.dto.response.NotificationListApiResponse;
import kr.ktb.zura.needu.notification.dto.response.NotificationReadResponse;
import kr.ktb.zura.needu.notification.dto.response.NotificationSummaryResponse;
import kr.ktb.zura.needu.notification.service.NotificationService;
import kr.ktb.zura.needu.notification.service.NotificationStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationStreamService notificationStreamService;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<NotificationSummaryResponse>> findNotificationSummary(
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.of(
                NotificationResponseMessage.NOTIFICATION_SUMMARY_FOUND.getMessage(),
                notificationService.findNotificationSummary(userId)
        ));
    }

    @GetMapping
    public ResponseEntity<NotificationListApiResponse> findAllNotifications(
            @AuthenticationPrincipal Long userId,
            @Valid @ModelAttribute NotificationListRequest request
    ) {
        return ResponseEntity.ok(NotificationListApiResponse.of(
                NotificationResponseMessage.NOTIFICATIONS_FOUND.getMessage(),
                notificationService.findAllNotifications(
                        userId, request.toCondition(NotificationPageLimits.DEFAULT_PAGE_SIZE))
        ));
    }

    @PatchMapping("/{notificationId}")
    public ResponseEntity<ApiResponse<NotificationReadResponse>> readNotification(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long notificationId
    ) {
        return ResponseEntity.ok(ApiResponse.of(
                NotificationResponseMessage.NOTIFICATION_READ.getMessage(),
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
