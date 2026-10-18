package kr.ktb.zura.needu.user.controller;

import jakarta.validation.Valid;
import kr.ktb.zura.needu.common.response.ApiResponse;
import kr.ktb.zura.needu.user.dto.request.UpdateConsentRequest;
import kr.ktb.zura.needu.user.dto.response.ConsentsResponse;
import kr.ktb.zura.needu.user.service.ConsentService;
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
@RequestMapping("/api/v1/users/me/consents")
public class ConsentController {

    private final ConsentService consentService;

    @GetMapping
    public ResponseEntity<ApiResponse<ConsentsResponse>> findConsents(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.of(
                UserResponseMessages.CONSENTS_FOUND,
                consentService.findConsents(userId)
        ));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<Void>> updateConsents(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UpdateConsentRequest request
    ) {
        consentService.updateConsents(userId, request);
        return ResponseEntity.ok(ApiResponse.of(UserResponseMessages.CONSENTS_SAVED, null));
    }
}
