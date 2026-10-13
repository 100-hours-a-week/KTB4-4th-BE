package kr.ktb.zura.needu.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import kr.ktb.zura.needu.auth.client.KakaoOAuthClient;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import kr.ktb.zura.needu.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthServiceTest {

    private KakaoOAuthClient kakaoOAuthClient;
    private UserService userService;
    private KakaoTokenService kakaoTokenService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        kakaoOAuthClient = mock(KakaoOAuthClient.class);
        userService = mock(UserService.class);
        kakaoTokenService = mock(KakaoTokenService.class);
        authService = new AuthService(
                kakaoOAuthClient,
                userService,
                mock(AuthSessionService.class),
                mock(AccessTokenService.class),
                kakaoTokenService
        );
    }

    @Test
    void validKakaoAccount_authorizeKakaoFriend_savesTokens() {
        when(kakaoOAuthClient.authorizeFriend("code")).thenReturn(authorization());

        String accessToken = authService.authorizeKakaoFriend(1L, "code");

        assertEquals("access-token", accessToken);
        verify(kakaoTokenService).saveTokens(1L, authorization().tokens());
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

    private KakaoOAuthClient.KakaoAuthorization authorization() {
        return new KakaoOAuthClient.KakaoAuthorization(
                new KakaoOAuthClient.KakaoUserInfo(99L, "tester", null),
                new KakaoOAuthClient.KakaoTokens(
                        "access-token",
                        "refresh-token",
                        Duration.ofHours(6),
                        Duration.ofDays(60)
                )
        );
    }
}
