package kr.ktb.zura.needu.notification.controller;

import kr.ktb.zura.needu.common.response.ApiResponse;
import kr.ktb.zura.needu.notification.dto.response.VapidPublicKeyResponse;
import kr.ktb.zura.needu.notification.service.VapidPublicKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications/web-push")
public class WebPushController {

    private final VapidPublicKeyService vapidPublicKeyService;

    @GetMapping("/public-key")
    public ResponseEntity<ApiResponse<VapidPublicKeyResponse>> findVapidPublicKey() {
        return ResponseEntity.ok(ApiResponse.of(
                NotificationResponseMessage.VAPID_PUBLIC_KEY_FOUND.getMessage(),
                vapidPublicKeyService.findVapidPublicKey()
        ));
    }
}
