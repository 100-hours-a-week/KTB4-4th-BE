package kr.ktb.zura.needu.user.service;

import kr.ktb.zura.needu.auth.service.AuthService;
import kr.ktb.zura.needu.user.dto.response.UserResponse;
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
