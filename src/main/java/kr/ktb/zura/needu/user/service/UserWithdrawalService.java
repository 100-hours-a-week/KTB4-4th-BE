package kr.ktb.zura.needu.user.service;

import kr.ktb.zura.needu.auth.service.AuthService;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.user.dto.response.UserResponse;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserWithdrawalService {

    private final UserService userService;
    private final AuthService authService;
    private final UserWithdrawalDataService userWithdrawalDataService;
    private final UserWithdrawalLock userWithdrawalLock;

    public void withdraw(Long userId) {
        // 테스트 계정은 여러 평가자가 함께 쓰고 카카오 계정도 없어 탈퇴하면 시드 데이터를 다시 넣어야 한다.
        if (authService.isDemoAccount(userId)) {
            throw new BusinessException(UserErrorCode.USER_DEMO_ACCOUNT_WITHDRAWAL_FORBIDDEN);
        }
        UserWithdrawalLock.Handle lock = userWithdrawalLock.lock(userId);
        try {
            UserResponse user = userService.findAuthenticatedUser(userId);
            authService.unlinkKakaoAccount(user.externalId());
            userWithdrawalDataService.deleteAndAnonymize(userId);
        } finally {
            userWithdrawalLock.unlock(lock);
        }
    }
}
