package kr.ktb.zura.needu.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserWithdrawalService {

    private final UserService userService;

    // TODO: 처리 중 중복 요청이면 USER_WITHDRAWAL_IN_PROGRESS
    //  카카오 연결 끊기를 먼저 시도하고 실패하면 탈퇴하지 않고 COMMON_SERVICE_UNAVAILABLE(정책 미결)
    //  성공하면 soft delete(User.withdraw), Refresh 세션 폐기, 웹 푸시 구독 삭제
    //  카카오 호출 중에는 DB 트랜잭션을 유지하지 않도록
    public void withdraw(Long userId, String refreshToken) {
        userService.validateAuthenticatableUser(userId);
        throw new UnsupportedOperationException("USER 탈퇴 미구현");
    }
}
