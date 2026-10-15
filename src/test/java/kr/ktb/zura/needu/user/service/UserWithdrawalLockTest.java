package kr.ktb.zura.needu.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import kr.ktb.zura.needu.user.repository.UserWithdrawalLockRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;

@ExtendWith(MockitoExtension.class)
class UserWithdrawalLockTest {

    private static final Long USER_ID = 101L;

    @Mock
    private UserWithdrawalLockRepository lockRepository;

    @InjectMocks
    private UserWithdrawalLock withdrawalLock;

    @Test
    void lockAvailable_lock_returnsHandleWithStoredOwnerToken() {
        given(lockRepository.create(eq(USER_ID), anyString())).willReturn(true);

        UserWithdrawalLock.Handle handle = withdrawalLock.lock(USER_ID);

        ArgumentCaptor<String> ownerToken = ArgumentCaptor.forClass(String.class);
        verify(lockRepository).create(eq(USER_ID), ownerToken.capture());
        assertThat(handle).isEqualTo(new UserWithdrawalLock.Handle(USER_ID, ownerToken.getValue()));
    }

    @Test
    void lockHeldByAnotherRequest_lock_throwsConflict() {
        given(lockRepository.create(eq(USER_ID), anyString())).willReturn(false);

        assertThatThrownBy(() -> withdrawalLock.lock(USER_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(UserErrorCode.USER_WITHDRAWAL_IN_PROGRESS));
    }

    @Test
    void redisUnavailable_lock_throwsServiceUnavailable() {
        given(lockRepository.create(eq(USER_ID), anyString()))
                .willThrow(new RedisConnectionFailureException("redis down"));

        assertThatThrownBy(() -> withdrawalLock.lock(USER_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(CommonErrorCode.COMMON_SERVICE_UNAVAILABLE));
    }

    @Test
    void heldLock_unlock_deletesWithOwnerToken() {
        UserWithdrawalLock.Handle handle = new UserWithdrawalLock.Handle(USER_ID, "owner-token");

        withdrawalLock.unlock(handle);

        verify(lockRepository).delete(USER_ID, "owner-token");
    }

    @Test
    void redisUnavailable_unlock_doesNotThrow() {
        UserWithdrawalLock.Handle handle = new UserWithdrawalLock.Handle(USER_ID, "owner-token");
        willThrow(new RedisConnectionFailureException("redis down"))
                .given(lockRepository).delete(USER_ID, "owner-token");

        assertThatCode(() -> withdrawalLock.unlock(handle)).doesNotThrowAnyException();
    }
}
