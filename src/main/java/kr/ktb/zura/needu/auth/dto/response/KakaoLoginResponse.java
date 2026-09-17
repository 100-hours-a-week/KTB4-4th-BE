package kr.ktb.zura.needu.auth.dto.response;

import kr.ktb.zura.needu.user.dto.response.UserResponse;

public record KakaoLoginResponse(
        Long userId,
        Long kakaoId,
        String nickname,
        String profileImageUrl,
        boolean onboardingCompleted
) {

    public static KakaoLoginResponse from(UserResponse user) {
        return new KakaoLoginResponse(user.id(), user.externalId(), user.nickname(),
                user.profileImageUrl(), user.onboardingCompleted());
    }
}
