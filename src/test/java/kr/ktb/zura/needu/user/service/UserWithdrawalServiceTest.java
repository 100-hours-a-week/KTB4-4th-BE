package kr.ktb.zura.needu.user.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import kr.ktb.zura.needu.auth.service.AuthService;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.user.dto.response.UserResponse;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class UserWithdrawalServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long KAKAO_ID = 42L;

    private final UserService userService = mock(UserService.class);
    private final AuthService authService = mock(AuthService.class);
    private final UserWithdrawalDataService dataService = mock(UserWithdrawalDataService.class);
    private final UserWithdrawalLock withdrawalLock = mock(UserWithdrawalLock.class);
    private final UserWithdrawalService service = new UserWithdrawalService(
            userService, authService, dataService, withdrawalLock);

    @Test
    void activeUser_withdraw_unlinksKakaoAndDeletesData() {
        UserWithdrawalLock.Handle handle = new UserWithdrawalLock.Handle(USER_ID, "owner-token");
        when(withdrawalLock.lock(USER_ID)).thenReturn(handle);
        when(userService.findAuthenticatedUser(USER_ID))
                .thenReturn(new UserResponse(USER_ID, KAKAO_ID, "니듀", null, true, null));

        service.withdraw(USER_ID);

        InOrder order = inOrder(withdrawalLock, userService, authService, dataService);
        order.verify(withdrawalLock).lock(USER_ID);
        order.verify(userService).findAuthenticatedUser(USER_ID);
        order.verify(authService).unlinkKakaoAccount(KAKAO_ID);
        order.verify(dataService).deleteAndAnonymize(USER_ID);
        order.verify(withdrawalLock).unlock(handle);
    }

    @Test
    void kakaoUnlinkFailed_withdraw_keepsLocalDataAndReleasesLock() {
        UserWithdrawalLock.Handle handle = new UserWithdrawalLock.Handle(USER_ID, "owner-token");
        when(withdrawalLock.lock(USER_ID)).thenReturn(handle);
        when(userService.findAuthenticatedUser(USER_ID))
                .thenReturn(new UserResponse(USER_ID, KAKAO_ID, "니듀", null, true, null));
        BusinessException exception = new BusinessException(CommonErrorCode.COMMON_SERVICE_UNAVAILABLE);
        org.mockito.Mockito.doThrow(exception).when(authService).unlinkKakaoAccount(KAKAO_ID);

        assertThrows(BusinessException.class, () -> service.withdraw(USER_ID));

        verify(dataService, never()).deleteAndAnonymize(USER_ID);
        verify(withdrawalLock).unlock(handle);
    }
}
