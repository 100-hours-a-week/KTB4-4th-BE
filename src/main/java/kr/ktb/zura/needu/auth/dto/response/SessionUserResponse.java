package kr.ktb.zura.needu.auth.dto.response;

import kr.ktb.zura.needu.user.dto.response.UserResponse;

public record SessionUserResponse(Long id, String nickname, String profileImageUrl, boolean onboardingCompleted) {

    public static SessionUserResponse from(UserResponse user) {
        return new SessionUserResponse(
                user.id(), user.nickname(), user.profileImageUrl(), user.onboardingCompleted());
    }
}
