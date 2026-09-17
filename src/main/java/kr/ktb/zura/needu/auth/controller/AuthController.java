package kr.ktb.zura.needu.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.util.UUID;
import kr.ktb.zura.needu.auth.dto.response.KakaoLoginResponse;
import kr.ktb.zura.needu.auth.service.AuthService;
import kr.ktb.zura.needu.auth.type.AuthErrorCode;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/kakao")
@RequiredArgsConstructor
public class AuthController {

    private static final String KAKAO_STATE = "kakaoOAuthState";

    private final AuthService authService;

    @GetMapping("/authorize")
    public ResponseEntity<Void> authorize(HttpServletRequest request) {
        String state = UUID.randomUUID().toString();
        request.getSession(true).setAttribute(KAKAO_STATE, state);
        URI authorizationUri = authService.createKakaoAuthorizationUri(state);
        return ResponseEntity.status(HttpStatus.FOUND).location(authorizationUri).build();
    }

    @GetMapping("/callback")
    public ResponseEntity<ApiResponse<KakaoLoginResponse>> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String expectedState = session == null ? null : (String) session.getAttribute(KAKAO_STATE);
        if (session != null) {
            session.invalidate();
        }
        if (expectedState == null || !expectedState.equals(state)) {
            throw new BusinessException(AuthErrorCode.AUTH_KAKAO_INVALID_STATE);
        }
        if (error != null) {
            throw new BusinessException(AuthErrorCode.AUTH_KAKAO_CANCELLED);
        }
        if (code == null || code.isBlank()) {
            throw new BusinessException(AuthErrorCode.AUTH_KAKAO_INVALID_CODE);
        }
        KakaoLoginResponse loginResponse = authService.loginWithKakao(code);
        return ResponseEntity.ok(ApiResponse.of("카카오 연동에 성공했습니다.", loginResponse));
    }
}
