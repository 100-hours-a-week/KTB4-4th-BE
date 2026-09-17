package kr.ktb.zura.needu.auth.controller;

import java.net.URI;
import kr.ktb.zura.needu.auth.dto.response.KakaoLoginResponse;
import kr.ktb.zura.needu.auth.service.AuthService;
import kr.ktb.zura.needu.auth.type.AuthErrorCode;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
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
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void authorize_redirectsToKakaoWithStoredState() throws Exception {
        when(authService.createKakaoAuthorizationUri(anyString()))
                .thenAnswer(invocation -> URI.create("https://kauth.kakao.com/oauth/authorize?state="
                        + invocation.getArgument(0)));

        MockHttpSession session = (MockHttpSession) mockMvc.perform(get("/api/v1/auth/kakao/authorize"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("https://kauth.kakao.com/oauth/authorize")))
                .andReturn().getRequest().getSession(false);

        assertNotNull(session.getAttribute("kakaoOAuthState"));
    }

    @Test
    void validCallback_returnsMemberJson() throws Exception {
        MockHttpSession session = sessionWithState("state-123");
        when(authService.loginWithKakao("valid-code"))
                .thenReturn(new KakaoLoginResponse(1L, 42L, "테스터", null, false));

        mockMvc.perform(get("/api/v1/auth/kakao/callback")
                        .session(session).param("code", "valid-code").param("state", "state-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("카카오 연동에 성공했습니다."))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.kakaoId").value(42))
                .andExpect(jsonPath("$.data.onboardingCompleted").value(false));
    }

    @Test
    void reusedState_returnsBadRequest() throws Exception {
        MockHttpSession session = sessionWithState("state-123");
        when(authService.loginWithKakao("valid-code"))
                .thenReturn(new KakaoLoginResponse(1L, 42L, "테스터", null, false));

        mockMvc.perform(get("/api/v1/auth/kakao/callback")
                        .session(session).param("code", "valid-code").param("state", "state-123"))
                .andExpect(status().isOk());
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
        when(authService.loginWithKakao("code"))
                .thenThrow(new BusinessException(AuthErrorCode.AUTH_KAKAO_UNAVAILABLE));

        mockMvc.perform(get("/api/v1/auth/kakao/callback")
                        .session(sessionWithState("state-123"))
                        .param("code", "code").param("state", "state-123"))
                .andExpect(status().isServiceUnavailable());
    }

    private MockHttpSession sessionWithState(String state) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("kakaoOAuthState", state);
        return session;
    }
}
