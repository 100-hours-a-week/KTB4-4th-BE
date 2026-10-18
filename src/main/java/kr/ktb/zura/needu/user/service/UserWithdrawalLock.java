package kr.ktb.zura.needu.user.service;

import java.util.UUID;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import kr.ktb.zura.needu.user.repository.UserWithdrawalLockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserWithdrawalLock {

    private final UserWithdrawalLockRepository lockRepository;

    public Handle lock(Long userId) {
        String ownerToken = UUID.randomUUID().toString();
        try {
            if (!lockRepository.create(userId, ownerToken)) {
                throw new BusinessException(UserErrorCode.USER_WITHDRAWAL_IN_PROGRESS);
            }
            return new Handle(userId, ownerToken);
        } catch (DataAccessException exception) {
            throw new BusinessException(CommonErrorCode.COMMON_SERVICE_UNAVAILABLE, null, exception);
        }
    }

    public void unlock(Handle handle) {
        try {
            lockRepository.delete(handle.userId(), handle.ownerToken());
        } catch (RuntimeException exception) {
            log.warn("User withdrawal lock release failed. userId={}", handle.userId(), exception);
        }
    }

    public record Handle(Long userId, String ownerToken) {
    }
}
