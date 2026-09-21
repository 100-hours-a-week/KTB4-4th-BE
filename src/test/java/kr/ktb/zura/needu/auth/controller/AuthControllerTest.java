package kr.ktb.zura.needu.auth.controller;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import kr.ktb.zura.needu.auth.dto.response.AuthSessionResponse;
import kr.ktb.zura.needu.auth.dto.response.SessionUserResponse;
import kr.ktb.zura.needu.auth.service.AuthService;
import kr.ktb.zura.needu.auth.exception.AuthErrorCode;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private AuthService authService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        when(authService.loginWithKakao(anyString()))
                .thenReturn(new AuthService.Tokens("access-token", "refresh-token", Duration.ofDays(14)));
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService, true, Duration.ofMinutes(15)))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validSession_returnsUser() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(1L, null));
        when(authService.findSession(1L)).thenReturn(new AuthSessionResponse(
                new SessionUserResponse(1L, "사용자", null), LocalDate.of(2000, 1, 1)));

        mockMvc.perform(get("/api/v1/auth/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("로그인 유효성을 조회했습니다."))
                .andExpect(jsonPath("$.data.user.id").value(1L))
                .andExpect(jsonPath("$.data.user.nickname").value("사용자"))
                .andExpect(jsonPath("$.data.user.profileImageUrl").isEmpty())
                .andExpect(jsonPath("$.data.birthday").value("2000-01-01"));
    }

    @Test
    void authorize_redirectsToKakaoWithStoredState() throws Exception {
        when(authService.createKakaoAuthorizationUri(anyString()))
                .thenAnswer(invocation -> URI.create("https://kauth.kakao.com/oauth/authorize?state="
                        + invocation.getArgument(0)));

        MockHttpSession session = (MockHttpSession) mockMvc.perform(get("/api/v1/auth/kakao/authorize")
                        .param("returnUrl", "https://needu.example.com/login?next=%2Fhome"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("https://kauth.kakao.com/oauth/authorize")))
                .andReturn().getRequest().getSession(false);

        assertNotNull(session.getAttribute("kakaoOAuthState"));
        assertEquals(URI.create("https://needu.example.com/login?next=%2Fhome"),
                session.getAttribute("kakaoOAuthReturnUrl"));
    }

    @Test
    void validCallback_redirectsToStoredReturnUrl() throws Exception {
        when(authService.createKakaoAuthorizationUri(anyString()))
                .thenReturn(URI.create("https://kauth.kakao.com/oauth/authorize"));
        MockHttpSession session = (MockHttpSession) mockMvc.perform(get("/api/v1/auth/kakao/authorize")
                        .param("returnUrl", "https://needu.example.com/login?next=%2Fhome"))
                .andReturn().getRequest().getSession(false);
        String state = (String) session.getAttribute("kakaoOAuthState");

        mockMvc.perform(get("/api/v1/auth/kakao/callback")
                        .session(session).param("code", "valid-code").param("state", state))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://needu.example.com/login?next=%2Fhome"))
                .andExpect(header().stringValues("Set-Cookie", hasItem(allOf(
                        containsString("NEEDU_ACCESS_TOKEN=access-token"),
                        containsString("Max-Age=900"), containsString("HttpOnly"),
                        containsString("Secure"), containsString("SameSite=Lax")))));
        verify(authService).loginWithKakao("valid-code");
    }

    @Test
    void reusedState_returnsBadRequest() throws Exception {
        MockHttpSession session = sessionWithState("state-123");
        mockMvc.perform(get("/api/v1/auth/kakao/callback")
                        .session(session).param("code", "valid-code").param("state", "state-123"))
                .andExpect(status().isFound());
        mockMvc.perform(get("/api/v1/auth/kakao/callback")
                        .param("code", "valid-code").param("state", "state-123"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidState_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/auth/kakao/callback")
                        .session(sessionWithState("expected"))
                        .param("code", "valid-code").param("state", "wrong"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(authService);
    }

    @Test
    void cancelledLogin_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/auth/kakao/callback")
                        .session(sessionWithState("state-123"))
                        .param("state", "state-123").param("error", "access_denied"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(authService);
    }

    @Test
    void kakaoFailure_returnsServiceUnavailable() throws Exception {
        doThrow(new BusinessException(AuthErrorCode.AUTH_KAKAO_UNAVAILABLE))
                .when(authService).loginWithKakao("code");

        mockMvc.perform(get("/api/v1/auth/kakao/callback")
                        .session(sessionWithState("state-123"))
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

    private MockHttpSession sessionWithState(String state) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("kakaoOAuthState", state);
        session.setAttribute("kakaoOAuthReturnUrl", URI.create("https://needu.example.com/login"));
        return session;
    }
}
