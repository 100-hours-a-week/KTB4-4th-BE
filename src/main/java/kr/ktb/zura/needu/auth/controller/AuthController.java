package kr.ktb.zura.needu.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import kr.ktb.zura.needu.auth.dto.response.CsrfTokenResponse;
import kr.ktb.zura.needu.auth.service.AuthService;
import kr.ktb.zura.needu.auth.exception.AuthErrorCode;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.response.ApiResponse;
import kr.ktb.zura.needu.common.security.AuthCookieNames;
import kr.ktb.zura.needu.user.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String KAKAO_STATE = "kakaoOAuthState";
    private static final String KAKAO_RETURN_URL = "kakaoOAuthReturnUrl";

    private final AuthService authService;
    private final boolean cookieSecure;
    private final Duration accessTokenExpiration;

    public AuthController(AuthService authService, @Value("${auth.cookie.secure}") boolean cookieSecure,
                          @Value("${auth.jwt.access-token-expiration}") Duration accessTokenExpiration) {
        this.authService = authService;
        this.cookieSecure = cookieSecure;
        this.accessTokenExpiration = accessTokenExpiration;
    }

    @GetMapping("/kakao/authorize")
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

    @GetMapping("/kakao/callback")
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
        AuthService.Tokens tokens = authService.loginWithKakao(code);
        return ResponseEntity.status(HttpStatus.FOUND).location(returnUri)
                .header(HttpHeaders.SET_COOKIE, accessCookie(tokens.accessToken(), accessTokenExpiration).toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie(tokens.refreshToken(), tokens.refreshMaxAge()).toString())
                .cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/csrf")
    public ResponseEntity<ApiResponse<CsrfTokenResponse>> csrf(@RequestAttribute("_csrf") CsrfToken csrfToken) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.of("CSRF 토큰을 조회했습니다.", CsrfTokenResponse.from(csrfToken)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(@CookieValue(name = AuthCookieNames.REFRESH_TOKEN, required = false)
                                        String refreshToken) {
        AuthService.Tokens tokens = authService.refresh(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie(tokens.accessToken(), accessTokenExpiration).toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie(tokens.refreshToken(), tokens.refreshMaxAge()).toString())
                .cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = AuthCookieNames.REFRESH_TOKEN, required = false)
                                       String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, accessCookie("", Duration.ZERO).toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
                .cacheControl(CacheControl.noStore()).build();
    }

    private ResponseCookie accessCookie(String value, Duration maxAge) {
        return ResponseCookie.from(AuthCookieNames.ACCESS_TOKEN, value)
                .httpOnly(true).secure(cookieSecure).sameSite("Lax").path("/").maxAge(maxAge).build();
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(AuthCookieNames.REFRESH_TOKEN, value)
                .httpOnly(true).secure(cookieSecure).sameSite("Lax")
                .path("/").maxAge(maxAge).build();
    }


}
