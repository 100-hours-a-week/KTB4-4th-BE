package kr.ktb.zura.needu.notification.controller;

import jakarta.validation.Valid;
import kr.ktb.zura.needu.common.response.ApiResponse;
import kr.ktb.zura.needu.notification.dto.request.CreatePushSubscriptionRequest;
import kr.ktb.zura.needu.notification.dto.request.DeletePushSubscriptionRequest;
import kr.ktb.zura.needu.notification.dto.response.PushSubscriptionResponse;
import kr.ktb.zura.needu.notification.service.PushSubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/me/push-subscriptions")
public class PushSubscriptionController {

    private final PushSubscriptionService pushSubscriptionService;

    @PostMapping
    public ResponseEntity<ApiResponse<PushSubscriptionResponse>> subscribe(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreatePushSubscriptionRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.of(
                NotificationResponseMessages.PUSH_SUBSCRIBED,
                pushSubscriptionService.subscribe(userId, request)
        ));
    }

    @DeleteMapping
    public ResponseEntity<Void> unsubscribe(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody DeletePushSubscriptionRequest request
    ) {
        pushSubscriptionService.unsubscribe(userId, request);
        return ResponseEntity.noContent().build();
    }
}
