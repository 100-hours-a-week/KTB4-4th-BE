package kr.ktb.zura.needu.user.service;

import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.user.dto.response.UserResponse;
import kr.ktb.zura.needu.user.entity.User;
import kr.ktb.zura.needu.user.repository.UserRepository;
import kr.ktb.zura.needu.user.type.Gender;
import kr.ktb.zura.needu.user.type.UserStatus;
import kr.ktb.zura.needu.user.type.UserErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final String DEFAULT_NICKNAME = "사용자";
    private static final int MAX_NICKNAME_LENGTH = 50;

    private final UserRepository userRepository;

    @Transactional
    public UserResponse findOrCreateKakaoUser(Long kakaoId, String nickname, String profileImageUrl) {
        User user = userRepository.findByExternalId(kakaoId)
                .orElseGet(() -> userRepository.save(new User(
                        kakaoId, normalizeNickname(nickname), profileImageUrl, Gender.NONE, null)));
        if (user.getStatus() == UserStatus.BLOCKED || user.getStatus() == UserStatus.WITHDRAWN) {
            throw new BusinessException(UserErrorCode.USER_UNAVAILABLE);
        }
        user.recordLogin();
        return UserResponse.from(user);
    }

    private String normalizeNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return DEFAULT_NICKNAME;
        }
        return nickname.length() > MAX_NICKNAME_LENGTH
                ? nickname.substring(0, MAX_NICKNAME_LENGTH) : nickname;
    }
}
