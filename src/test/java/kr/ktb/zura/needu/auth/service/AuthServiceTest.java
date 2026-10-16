package kr.ktb.zura.needu.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import kr.ktb.zura.needu.auth.client.KakaoOAuthClient;
import kr.ktb.zura.needu.auth.config.KakaoOAuth2ClientConfig;
import kr.ktb.zura.needu.auth.exception.AuthErrorCode;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import kr.ktb.zura.needu.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

class AuthServiceTest {

    private KakaoOAuthClient kakaoOAuthClient;
    private UserService userService;
    private AuthSessionService authSessionService;
    private KakaoTokenService kakaoTokenService;
    private OAuth2AuthorizedClientManager authorizedClientManager;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        kakaoOAuthClient = mock(KakaoOAuthClient.class);
        userService = mock(UserService.class);
        authSessionService = mock(AuthSessionService.class);
        kakaoTokenService = mock(KakaoTokenService.class);
        authorizedClientManager = mock(OAuth2AuthorizedClientManager.class);
        authService = new AuthService(
                kakaoOAuthClient,
                userService,
                authSessionService,
                mock(AccessTokenService.class),
                kakaoTokenService,
                authorizedClientManager
        );
    }

    @Test
    void validKakaoAccount_authorizeKakaoFriend_savesTokens() {
        when(kakaoOAuthClient.authorizeFriend("code")).thenReturn(authorization());

        authService.authorizeKakaoFriend(1L, "code");

        verify(kakaoTokenService).saveTokens(1L, authorization().tokens());
    }

    @Test
    void authorizedFriendScope_findKakaoAccessToken_returnsManagedToken() {
        when(authorizedClientManager.authorize(org.mockito.ArgumentMatchers.any()))
                .thenReturn(authorizedClient(Set.of("friends")));

        String accessToken = authService.findKakaoAccessToken(1L, "friends");

        assertEquals("managed-access", accessToken);
    }

    @Test
    void missingRequiredScope_findKakaoAccessToken_requiresAuthorization() {
        when(authorizedClientManager.authorize(org.mockito.ArgumentMatchers.any()))
                .thenReturn(authorizedClient(Set.of("profile_nickname")));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.findKakaoAccessToken(1L, "friends"));

        assertEquals(AuthErrorCode.AUTH_KAKAO_AUTHORIZATION_REQUIRED, exception.getErrorCode());
    }

    @Test
    void differentKakaoAccount_authorizeKakaoFriend_doesNotSaveTokens() {
        when(kakaoOAuthClient.authorizeFriend("code")).thenReturn(authorization());
        doThrow(new BusinessException(UserErrorCode.USER_KAKAO_ACCOUNT_MISMATCH))
                .when(userService).validateKakaoIdentity(1L, 99L);

        assertThrows(BusinessException.class, () -> authService.authorizeKakaoFriend(1L, "code"));

        verify(kakaoTokenService, never()).saveTokens(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void existingUser_deleteUserAuthentication_deletesSessionsAndKakaoTokens() {
        authService.deleteUserAuthentication(1L);

        verify(authSessionService).deleteAllByUserId(1L);
        verify(kakaoTokenService).deleteByUserId(1L);
    }

    private KakaoOAuthClient.KakaoAuthorization authorization() {
        return new KakaoOAuthClient.KakaoAuthorization(
                new KakaoOAuthClient.KakaoUserInfo(99L, "tester", null),
                new KakaoOAuthClient.KakaoTokens(
                        "access-token",
                        "refresh-token",
                        Duration.ofHours(6),
                        Duration.ofDays(60),
                        Set.of("friends")
                )
        );
    }

    private OAuth2AuthorizedClient authorizedClient(Set<String> scopes) {
        ClientRegistration registration = ClientRegistration
                .withRegistrationId(KakaoOAuth2ClientConfig.REGISTRATION_ID)
                .clientId("client-id")
                .clientSecret("client-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://example.com/callback")
                .authorizationUri("https://kauth.kakao.com/oauth/authorize")
                .tokenUri("https://kauth.kakao.com/oauth/token")
                .build();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "managed-access",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                scopes);
        return new OAuth2AuthorizedClient(registration, "1", accessToken);
    }
}
