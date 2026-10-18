package kr.ktb.zura.needu.user.controller;

import jakarta.validation.Valid;
import kr.ktb.zura.needu.common.response.ApiResponse;
import kr.ktb.zura.needu.user.dto.request.UpdateGiftPreferenceRequest;
import kr.ktb.zura.needu.user.dto.response.GiftPreferenceResponse;
import kr.ktb.zura.needu.user.dto.response.GiftPreferenceResultResponse;
import kr.ktb.zura.needu.user.service.GiftPreferenceService;
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
@RequestMapping("/api/v1/users/me/gift-preferences")
public class GiftPreferenceController {

    private final GiftPreferenceService giftPreferenceService;

    @GetMapping
    public ResponseEntity<ApiResponse<GiftPreferenceResponse>> findGiftPreference(
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.of(
                UserResponseMessages.GIFT_PREFERENCE_FOUND,
                giftPreferenceService.findGiftPreference(userId)
        ));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<GiftPreferenceResultResponse>> updateGiftPreference(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UpdateGiftPreferenceRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.of(
                UserResponseMessages.GIFT_PREFERENCE_UPDATED,
                giftPreferenceService.updateGiftPreference(userId, request)
        ));
    }
}
