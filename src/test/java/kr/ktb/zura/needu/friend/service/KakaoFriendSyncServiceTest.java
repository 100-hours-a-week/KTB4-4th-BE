package kr.ktb.zura.needu.friend.service;

import kr.ktb.zura.needu.auth.service.AuthService;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.friend.client.KakaoFriendClient;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import kr.ktb.zura.needu.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
        doThrow(new BusinessException(UserErrorCode.USER_KAKAO_ACCOUNT_MISMATCH))
                .when(authService).authorizeKakaoFriend(1L, "code");

        assertThrows(BusinessException.class, () -> syncService.sync(1L, "code"));

        verify(kakaoFriendClient, never()).findAllFriendIds(org.mockito.ArgumentMatchers.anyString());
        verify(friendService, never()).syncKakaoFriends(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyList());
    }
}
