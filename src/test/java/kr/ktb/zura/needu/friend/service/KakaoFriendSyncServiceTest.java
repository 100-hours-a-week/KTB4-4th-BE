package kr.ktb.zura.needu.friend.service;

import kr.ktb.zura.needu.auth.service.AuthService;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.friend.client.KakaoFriendClient;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import kr.ktb.zura.needu.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KakaoFriendSyncServiceTest {

    private AuthService authService;
    private UserService userService;
    private KakaoFriendClient kakaoFriendClient;
    private FriendService friendService;
    private KakaoFriendSyncService syncService;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        userService = mock(UserService.class);
        kakaoFriendClient = mock(KakaoFriendClient.class);
        friendService = mock(FriendService.class);
        syncService = new KakaoFriendSyncService(authService, userService, kakaoFriendClient, friendService);
    }

    @Test
    void differentKakaoAccount_doesNotRequestOrSaveFriends() {
        when(authService.authorizeKakaoFriend("code"))
                .thenReturn(new AuthService.KakaoAuthorization(99L, "kakao-access"));
        doThrow(new BusinessException(UserErrorCode.USER_KAKAO_ACCOUNT_MISMATCH))
                .when(userService).validateKakaoIdentity(1L, 99L);

        assertThrows(BusinessException.class, () -> syncService.sync(1L, "code"));

        verify(kakaoFriendClient, never()).findAllFriendIds("kakao-access");
        verify(friendService, never()).addKakaoFriends(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyList());
    }
}
