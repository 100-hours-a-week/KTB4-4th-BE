package kr.ktb.zura.needu.auth.controller;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import kr.ktb.zura.needu.auth.client.KakaoOAuthClient;
import kr.ktb.zura.needu.auth.repository.AuthSessionRepository;
import kr.ktb.zura.needu.common.security.AuthCookieNames;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AuthFlowIntegrationTest {

    @MockitoBean
    private KakaoOAuthClient kakaoOAuthClient;

    private final MockMvc mockMvc;
    private final AuthSessionRepository authSessionRepository;

    AuthFlowIntegrationTest(MockMvc mockMvc, AuthSessionRepository authSessionRepository) {
        this.mockMvc = mockMvc;
        this.authSessionRepository = authSessionRepository;
    }

    @Test
    void kakaoLogin_refreshAndLogout_rotatesCookieTokensWithCsrfProtection() throws Exception {
        when(kakaoOAuthClient.createAuthorizationUri(anyString()))
                .thenReturn(URI.create("https://kauth.kakao.com/oauth/authorize"));
        when(kakaoOAuthClient.findUserInfo("valid-code"))
                .thenReturn(new KakaoOAuthClient.KakaoUserInfo(987654321L, "tester", null));

        MvcResult csrf = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk()).andReturn();
        String csrfToken = JsonPath.read(csrf.getResponse().getContentAsString(), "$.data.token");
        Cookie csrfCookie = csrf.getResponse().getCookie("NEEDU_CSRF");
        assertThat(csrfCookie).isNotNull();
        assertThat(csrfCookie.isHttpOnly()).isTrue();

        MockHttpSession oauthSession = (MockHttpSession) mockMvc.perform(get("/api/v1/auth/kakao/authorize")
                        .param("returnUrl", "https://needu.example.com/login"))
                .andExpect(status().isFound()).andReturn().getRequest().getSession(false);
        MvcResult login = mockMvc.perform(get("/api/v1/auth/kakao/callback")
                        .session(oauthSession).param("code", "valid-code")
                        .param("state", (String) oauthSession.getAttribute("kakaoOAuthState")))
                .andExpect(status().isFound()).andReturn();
        Cookie accessCookie = login.getResponse().getCookie(AuthCookieNames.ACCESS_TOKEN);
        Cookie refreshCookie = login.getResponse().getCookie(AuthCookieNames.REFRESH_TOKEN);
        assertThat(accessCookie).isNotNull();
        assertThat(refreshCookie).isNotNull();
        assertThat(accessCookie.isHttpOnly()).isTrue();
        assertThat(refreshCookie.isHttpOnly()).isTrue();
        assertThat(refreshCookie.getPath()).isEqualTo("/");
        assertThat(login.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).allSatisfy(
                header -> assertThat(header).contains("SameSite=Lax"));
        assertThat(authSessionRepository.findAll()).hasSize(1);
        assertThat(authSessionRepository.findAll().getFirst().getRefreshTokenHash()).hasSize(32)
                .isNotEqualTo(refreshCookie.getValue().getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/api/v1/guidance").cookie(accessCookie))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie, csrfCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("접근 권한이 없습니다."));

        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie, csrfCookie,
                                new Cookie(AuthCookieNames.ACCESS_TOKEN, "expired"))
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isOk()).andReturn();
        Cookie nextRefreshCookie = refreshed.getResponse().getCookie(AuthCookieNames.REFRESH_TOKEN);
        assertThat(nextRefreshCookie).isNotNull();
        assertThat(nextRefreshCookie.getValue()).isNotEqualTo(refreshCookie.getValue());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie, csrfCookie).header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isUnauthorized());
        MvcResult logout = mockMvc.perform(delete("/api/v1/auth/session")
                        .cookie(nextRefreshCookie, csrfCookie).header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isNoContent()).andReturn();
        assertThat(logout.getResponse().getCookie(AuthCookieNames.ACCESS_TOKEN).getMaxAge()).isZero();
        assertThat(logout.getResponse().getCookie(AuthCookieNames.REFRESH_TOKEN).getMaxAge()).isZero();
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(nextRefreshCookie, csrfCookie).header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isUnauthorized());
    }
}
