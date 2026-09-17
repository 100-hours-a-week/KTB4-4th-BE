package kr.ktb.zura.needu.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.util.UUID;
import kr.ktb.zura.needu.auth.service.AuthService;
import kr.ktb.zura.needu.auth.exception.AuthErrorCode;
import kr.ktb.zura.needu.common.exception.BusinessException;
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
    private static final String KAKAO_RETURN_URL = "kakaoOAuthReturnUrl";

    private final AuthService authService;

    @GetMapping("/authorize")
    public ResponseEntity<Void> authorize(@RequestParam String returnUrl, HttpServletRequest request) {
        URI returnUri;
        try {
            returnUri = URI.create(returnUrl);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(AuthErrorCode.AUTH_KAKAO_INVALID_RETURN_URL);
        }
        if (!("http".equalsIgnoreCase(returnUri.getScheme()) || "https".equalsIgnoreCase(returnUri.getScheme()))
                || returnUri.getHost() == null || returnUri.getUserInfo() != null) {
            throw new BusinessException(AuthErrorCode.AUTH_KAKAO_INVALID_RETURN_URL);
        }
        String state = UUID.randomUUID().toString();
        HttpSession session = request.getSession(true);
        session.setAttribute(KAKAO_STATE, state);
        session.setAttribute(KAKAO_RETURN_URL, returnUri);
        URI authorizationUri = authService.createKakaoAuthorizationUri(state);
        return ResponseEntity.status(HttpStatus.FOUND).location(authorizationUri).build();
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String expectedState = session == null ? null : (String) session.getAttribute(KAKAO_STATE);
        URI returnUri = session == null ? null : (URI) session.getAttribute(KAKAO_RETURN_URL);
        if (session != null) {
            session.invalidate();
        }
        if (expectedState == null || !expectedState.equals(state) || returnUri == null) {
            throw new BusinessException(AuthErrorCode.AUTH_KAKAO_INVALID_STATE);
        }
        if (error != null) {
            throw new BusinessException(AuthErrorCode.AUTH_KAKAO_CANCELLED);
        }
        if (code == null || code.isBlank()) {
            throw new BusinessException(AuthErrorCode.AUTH_KAKAO_INVALID_CODE);
        }
        authService.loginWithKakao(code);
        return ResponseEntity.status(HttpStatus.FOUND).location(returnUri).build();
    }
}
