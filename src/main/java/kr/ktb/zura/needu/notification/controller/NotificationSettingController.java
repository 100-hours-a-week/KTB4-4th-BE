package kr.ktb.zura.needu.notification.controller;

import jakarta.validation.Valid;
import kr.ktb.zura.needu.common.response.ApiResponse;
import kr.ktb.zura.needu.notification.dto.request.UpdateNotificationSettingRequest;
import kr.ktb.zura.needu.notification.dto.response.NotificationSettingResponse;
import kr.ktb.zura.needu.notification.service.NotificationSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/me/settings/notifications")
public class NotificationSettingController {

    private final NotificationSettingService notificationSettingService;

    @GetMapping
    public ResponseEntity<ApiResponse<NotificationSettingResponse>> findNotificationSetting(
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.of(
                NotificationResponseMessages.NOTIFICATION_SETTING_FOUND,
                notificationSettingService.findNotificationSetting(userId)
        ));
    }

    @PatchMapping
    public ResponseEntity<ApiResponse<NotificationSettingResponse>> updateNotificationSetting(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UpdateNotificationSettingRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.of(
                NotificationResponseMessages.NOTIFICATION_SETTING_UPDATED,
                notificationSettingService.updateNotificationSetting(userId, request)
        ));
    }
}
