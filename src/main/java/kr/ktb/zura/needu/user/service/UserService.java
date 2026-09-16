package kr.ktb.zura.needu.user.service;

import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.user.dto.response.UserSummaryResponse;
import kr.ktb.zura.needu.user.entity.User;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import kr.ktb.zura.needu.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserSummaryResponse findUserSummary(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        validateActiveUser(user);

        return UserSummaryResponse.from(user);
    }

    private void validateActiveUser(User user) {
        switch (user.getStatus()) {
            case ACTIVE -> {
            }
            case ONBOARDING -> throw new BusinessException(UserErrorCode.USER_ONBOARDING_REQUIRED);
            case BLOCKED -> throw new BusinessException(UserErrorCode.USER_BLOCKED);
            case WITHDRAWN -> throw new BusinessException(UserErrorCode.USER_WITHDRAWN);
        }
    }
}
