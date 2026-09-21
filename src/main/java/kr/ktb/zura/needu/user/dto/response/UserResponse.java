package kr.ktb.zura.needu.user.dto.response;

import java.time.LocalDate;
import kr.ktb.zura.needu.user.entity.User;

public record UserResponse(
        Long id,
        Long externalId,
        String nickname,
        String profileImageUrl,
        boolean onboardingCompleted,
        LocalDate birthDate
) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getExternalId(), user.getNickname(),
                user.getProfileImageUrl(), user.isOnboardingCompleted(), user.getBirthDate());
    }
}
