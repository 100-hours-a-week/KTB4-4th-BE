package kr.ktb.zura.needu.user.service;

import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.user.dto.response.UserResponse;
import kr.ktb.zura.needu.user.dto.response.UserSummaryResponse;
import kr.ktb.zura.needu.user.entity.User;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import kr.ktb.zura.needu.user.repository.UserRepository;
import kr.ktb.zura.needu.user.type.Gender;
import kr.ktb.zura.needu.user.type.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private static final String DEFAULT_NICKNAME = "사용자";
    private static final int MAX_NICKNAME_LENGTH = 50;

    private final UserRepository userRepository;

    @Transactional
    public UserResponse findOrCreateKakaoUser(Long kakaoId, String nickname, String profileImageUrl) {
        User user = userRepository.findByExternalId(kakaoId)
                .orElseGet(() -> userRepository.save(new User(
                        kakaoId, normalizeNickname(nickname), profileImageUrl, Gender.NONE, null)));
        validateAuthenticatableUser(user);
        user.recordLogin();
        return UserResponse.from(user);
    }

    public void validateAuthenticatableUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        validateAuthenticatableUser(user);
    }

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

    private void validateAuthenticatableUser(User user) {
        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new BusinessException(UserErrorCode.USER_BLOCKED);
        }
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new BusinessException(UserErrorCode.USER_WITHDRAWN);
        }
    }

    private String normalizeNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return DEFAULT_NICKNAME;
        }
        return nickname.length() > MAX_NICKNAME_LENGTH
                ? nickname.substring(0, MAX_NICKNAME_LENGTH) : nickname;
    }
}
