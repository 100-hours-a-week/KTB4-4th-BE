package kr.ktb.zura.needu.auth.controller;

import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import kr.ktb.zura.needu.auth.dto.response.AuthSessionResponse;
import kr.ktb.zura.needu.auth.dto.response.SessionUserResponse;
import kr.ktb.zura.needu.auth.service.AuthService;
import kr.ktb.zura.needu.auth.service.KakaoOAuthTransactionService;
import kr.ktb.zura.needu.auth.type.KakaoOAuthContext;
import kr.ktb.zura.needu.auth.exception.AuthErrorCode;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private AuthService authService;
    private KakaoOAuthTransactionService kakaoOAuthTransactionService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        kakaoOAuthTransactionService = mock(KakaoOAuthTransactionService.class);
        when(kakaoOAuthTransactionService.getTtl()).thenReturn(Duration.ofMinutes(10));
        when(authService.loginWithKakao(anyString()))
                .thenReturn(new AuthService.Tokens("access-token", "refresh-token", Duration.ofDays(14)));
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(
                        authService, kakaoOAuthTransactionService, true, Duration.ofMinutes(15)))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validDemoAccount_loginWithDemoAccount_setsAuthCookies() throws Exception {
        when(authService.loginWithDemoAccount("reviewer1", "password"))
                .thenReturn(new AuthService.Tokens("access-token", "refresh-token", Duration.ofDays(14)));

        mockMvc.perform(post("/api/v1/auth/demo/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"reviewer1","password":"password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("로그인했습니다."))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(header().stringValues("Set-Cookie", hasItem(allOf(
                        containsString("NEEDU_ACCESS_TOKEN=access-token"), containsString("HttpOnly")))))
                .andExpect(header().stringValues("Set-Cookie", hasItem(allOf(
                        containsString("NEEDU_REFRESH_TOKEN=refresh-token"), containsString("Max-Age=1209600")))))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void validRefreshToken_refresh_returnsMessageAndRotatesCookies() throws Exception {
        String refreshToken = "a".repeat(43);
        when(authService.refresh(refreshToken))
                .thenReturn(new AuthService.Tokens("next-access-token", "next-refresh-token", Duration.ofDays(13)));

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("NEEDU_REFRESH_TOKEN", refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("로그인을 연장했습니다."))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(header().stringValues("Set-Cookie", hasItem(
                        containsString("NEEDU_ACCESS_TOKEN=next-access-token"))))
                .andExpect(header().stringValues("Set-Cookie", hasItem(
                        containsString("NEEDU_REFRESH_TOKEN=next-refresh-token"))))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void blankPassword_loginWithDemoAccount_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(post("/api/v1/auth/demo/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"reviewer1","password":""}
                                """))
                .andExpect(status().isUnprocessableContent());

        verify(authService, org.mockito.Mockito.never()).loginWithDemoAccount(anyString(), anyString());
    }

    @Test
    void wrongDemoCredentials_loginWithDemoAccount_returnsUnauthorized() throws Exception {
        when(authService.loginWithDemoAccount("reviewer1", "wrong"))
                .thenThrow(new BusinessException(AuthErrorCode.AUTH_DEMO_LOGIN_FAILED));

        mockMvc.perform(post("/api/v1/auth/demo/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"reviewer1","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validSession_returnsUser() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(1L, null));
        when(authService.findSession(1L)).thenReturn(new AuthSessionResponse(
                new SessionUserResponse(1L, "사용자", null, false), LocalDate.of(2000, 1, 1)));

        mockMvc.perform(get("/api/v1/auth/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("로그인 유효성을 조회했습니다."))
                .andExpect(jsonPath("$.data.user.id").value(1L))
                .andExpect(jsonPath("$.data.user.nickname").value("사용자"))
                .andExpect(jsonPath("$.data.user.profileImageUrl").isEmpty())
                .andExpect(jsonPath("$.data.user.onboardingCompleted").value(false))
                .andExpect(jsonPath("$.data.birthday").value("2000-01-01"));
    }

    @Test
    void authorize_redirectsToKakaoWithStoredState() throws Exception {
        when(kakaoOAuthTransactionService.createLoginState("https://needu.example.com/login?next=%2Fhome"))
                .thenReturn("state-123");
        when(authService.createKakaoAuthorizationUri(anyString()))
                .thenAnswer(invocation -> URI.create("https://kauth.kakao.com/oauth/authorize?state="
                        + invocation.getArgument(0)));

        mockMvc.perform(get("/api/v1/auth/kakao/authorize")
                        .param("returnUrl", "https://needu.example.com/login?next=%2Fhome"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("https://kauth.kakao.com/oauth/authorize")))
                .andExpect(header().stringValues("Set-Cookie", hasItem(allOf(
                        containsString("NEEDU_KAKAO_LOGIN_OAUTH=state-123"),
                        containsString("Path=/api/v1/auth/kakao/callback"),
                        containsString("Max-Age=600"), containsString("HttpOnly"),
                        containsString("Secure"), containsString("SameSite=Lax")))));
    }

    @Test
    void validCallback_redirectsToStoredReturnUrl() throws Exception {
        when(kakaoOAuthTransactionService.consumeLoginState("state-123", "state-123"))
                .thenReturn(Optional.of(context("https://needu.example.com/login?next=%2Fhome")));

        mockMvc.perform(get("/api/v1/auth/kakao/callback")
                        .cookie(oauthCookie()).param("code", "valid-code").param("state", "state-123"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://needu.example.com/login?next=%2Fhome"))
                .andExpect(header().stringValues("Set-Cookie", hasItem(allOf(
                        containsString("NEEDU_ACCESS_TOKEN=access-token"),
                        containsString("Max-Age=900"), containsString("HttpOnly"),
                        containsString("Secure"), containsString("SameSite=Lax")))))
                .andExpect(header().stringValues("Set-Cookie", hasItem(allOf(
                        containsString("NEEDU_KAKAO_LOGIN_OAUTH="), containsString("Max-Age=0")))));
        verify(authService).loginWithKakao("valid-code");
    }

    @Test
    void reusedState_returnsBadRequest() throws Exception {
        when(kakaoOAuthTransactionService.consumeLoginState("state-123", "state-123"))
                .thenReturn(Optional.of(context("https://needu.example.com/login")), Optional.empty());
        mockMvc.perform(get("/api/v1/auth/kakao/callback")
                        .cookie(oauthCookie()).param("code", "valid-code").param("state", "state-123"))
                .andExpect(status().isFound());
        mockMvc.perform(get("/api/v1/auth/kakao/callback")
                        .cookie(oauthCookie()).param("code", "valid-code").param("state", "state-123"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidState_returnsBadRequest() throws Exception {
        when(kakaoOAuthTransactionService.consumeLoginState("wrong", "expected")).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/v1/auth/kakao/callback")
                        .cookie(new Cookie("NEEDU_KAKAO_LOGIN_OAUTH", "expected"))
                        .param("code", "valid-code").param("state", "wrong"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(authService);
    }

    @Test
    void cancelledLogin_returnsBadRequest() throws Exception {
        when(kakaoOAuthTransactionService.consumeLoginState("state-123", "state-123"))
                .thenReturn(Optional.of(context("https://needu.example.com/login")));
        mockMvc.perform(get("/api/v1/auth/kakao/callback")
                        .cookie(oauthCookie())
                        .param("state", "state-123").param("error", "access_denied"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(authService);
    }

    @Test
    void kakaoFailure_returnsServiceUnavailable() throws Exception {
        when(kakaoOAuthTransactionService.consumeLoginState("state-123", "state-123"))
                .thenReturn(Optional.of(context("https://needu.example.com/login")));
        doThrow(new BusinessException(AuthErrorCode.AUTH_KAKAO_UNAVAILABLE))
                .when(authService).loginWithKakao("code");

        mockMvc.perform(get("/api/v1/auth/kakao/callback")
                        .cookie(oauthCookie())
                        .param("code", "code").param("state", "state-123"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void invalidReturnUrl_returnsBadRequestWithoutStartingLogin() throws Exception {
        mockMvc.perform(get("/api/v1/auth/kakao/authorize")
                        .param("returnUrl", "javascript:alert(1)"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(authService);
    }

    private Cookie oauthCookie() {
        return new Cookie("NEEDU_KAKAO_LOGIN_OAUTH", "state-123");
    }

    private KakaoOAuthContext context(String returnUrl) {
        return new KakaoOAuthContext(returnUrl, null);
    }
}
