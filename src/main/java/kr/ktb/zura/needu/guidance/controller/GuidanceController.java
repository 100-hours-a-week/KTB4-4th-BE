package kr.ktb.zura.needu.guidance.controller;

import kr.ktb.zura.needu.common.response.ApiResponse;
import kr.ktb.zura.needu.guidance.dto.response.GuidanceResponse;
import kr.ktb.zura.needu.guidance.service.GuidanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/guidance")
public class GuidanceController {

    private static final String GUIDANCE_FOUND_MESSAGE = "메인 정보를 조회했습니다.";

    private final GuidanceService guidanceService;

    @GetMapping
    public ResponseEntity<ApiResponse<GuidanceResponse>> findGuidance(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.of(GUIDANCE_FOUND_MESSAGE, guidanceService.findGuidance(userId)));
    }
}
