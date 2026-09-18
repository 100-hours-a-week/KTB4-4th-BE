package kr.ktb.zura.needu.common.security;

import java.net.URI;
import java.time.Instant;
import java.time.Duration;
import jakarta.servlet.http.Cookie;
import kr.ktb.zura.needu.auth.service.AccessTokenService;
import kr.ktb.zura.needu.auth.service.AuthService;
import kr.ktb.zura.needu.guidance.service.GuidanceService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class JwtAuthenticationIntegrationTest {

    @MockitoBean
    private GuidanceService guidanceService;

    @MockitoBean
    private AuthService authService;

    private final MockMvc mockMvc;
    private final AccessTokenService accessTokenService;
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;

    JwtAuthenticationIntegrationTest(MockMvc mockMvc, AccessTokenService accessTokenService,
                                     JwtEncoder jwtEncoder, JwtDecoder jwtDecoder) {
        this.mockMvc = mockMvc;
        this.accessTokenService = accessTokenService;
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
    }

    @Test
    void validUserId_issuesTokenWithExpectedClaims() {
        String token = accessTokenService.issueAccessToken(42L);
        var jwt = jwtDecoder.decode(token);

        assertThat(((Number) jwt.getClaim("userId")).longValue()).isEqualTo(42L);
        assertThat(jwt.getIssuedAt()).isNotNull();
        assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plusSeconds(15 * 60));
        assertThat(jwt.getClaims()).doesNotContainKey("sid");
        assertThrows(IllegalArgumentException.class, () -> accessTokenService.issueAccessToken(0L));
    }

    @Test
    void validAccessCookie_passesLongUserIdToController() throws Exception {
        mockMvc.perform(get("/api/v1/guidance")
                        .cookie(new Cookie(AuthCookieNames.ACCESS_TOKEN, accessTokenService.issueAccessToken(42L))))
                .andExpect(status().isOk());

        verify(guidanceService).findGuidance(42L);
    }

    @Test
    void statelessSecurity_keepsKakaoOAuthStateSession() throws Exception {
        when(authService.createKakaoAuthorizationUri(anyString()))
                .thenReturn(URI.create("https://kauth.kakao.com/oauth/authorize"));
        when(authService.loginWithKakao("valid-code"))
                .thenReturn(new AuthService.Tokens("access-token", "refresh-token", Duration.ofDays(14)));
        MockHttpSession session = (MockHttpSession) mockMvc.perform(get("/api/v1/auth/kakao/authorize")
                        .param("returnUrl", "https://needu.example.com/login"))
                .andExpect(status().isFound())
                .andReturn().getRequest().getSession(false);

        mockMvc.perform(get("/api/v1/auth/kakao/callback")
                        .session(session)
                        .param("code", "valid-code")
                        .param("state", (String) session.getAttribute("kakaoOAuthState")))
                .andExpect(status().isFound());

        verify(authService).loginWithKakao("valid-code");
    }

    @Test
    void missingOrInvalidAccessCookie_returnsUnauthorized() throws Exception {
        String token = accessTokenService.issueAccessToken(42L);
        String[] segments = token.split("\\.");
        segments[2] = (segments[2].startsWith("A") ? "B" : "A") + segments[2].substring(1);
        String tamperedToken = String.join(".", segments);
        Instant now = Instant.now();
        String expiredToken = encode(JwtClaimsSet.builder().claim("userId", 42L)
                .issuedAt(now.minusSeconds(1800)).expiresAt(now.minusSeconds(120)).build());
        String missingUserIdToken = encode(JwtClaimsSet.builder()
                .issuedAt(now).expiresAt(now.plusSeconds(900)).build());
        String missingExpirationToken = encode(JwtClaimsSet.builder()
                .claim("userId", 42L).issuedAt(now).build());
        String stringUserIdToken = encode(JwtClaimsSet.builder().claim("userId", "42")
                .issuedAt(now).expiresAt(now.plusSeconds(900)).build());

        mockMvc.perform(get("/api/v1/guidance"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));
        for (String invalidToken : new String[]{"malformed", tamperedToken, expiredToken,
                missingUserIdToken, missingExpirationToken, stringUserIdToken}) {
            mockMvc.perform(get("/api/v1/guidance")
                            .cookie(new Cookie(AuthCookieNames.ACCESS_TOKEN, invalidToken)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));
        }
    }

    private String encode(JwtClaimsSet claims) {
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
