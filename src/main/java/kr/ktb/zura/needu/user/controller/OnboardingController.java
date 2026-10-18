package kr.ktb.zura.needu.user.controller;

import jakarta.validation.Valid;
import kr.ktb.zura.needu.common.response.ApiResponse;
import kr.ktb.zura.needu.user.dto.request.CompleteOnboardingRequest;
import kr.ktb.zura.needu.user.dto.response.OnboardingStatusResponse;
import kr.ktb.zura.needu.user.service.OnboardingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/me/onboarding")
public class OnboardingController {

    private final OnboardingService onboardingService;

    @GetMapping
    public ResponseEntity<ApiResponse<OnboardingStatusResponse>> findOnboardingStatus(
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.of(
                UserResponseMessages.ONBOARDING_STATUS_FOUND,
                onboardingService.findOnboardingStatus(userId)
        ));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<Void>> completeOnboarding(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CompleteOnboardingRequest request
    ) {
        onboardingService.completeOnboarding(userId, request);
        return ResponseEntity.ok(ApiResponse.of(UserResponseMessages.ONBOARDING_COMPLETED, null));
    }
}
