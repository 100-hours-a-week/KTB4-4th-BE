package kr.ktb.zura.needu.friend.dto.response;

import java.time.LocalDate;
import kr.ktb.zura.needu.user.dto.response.UserDetailResponse;

public record FriendDetailResponse(
        Long id,
        String nickname,
        String profileImageUrl,
        boolean tasteAnalysisCompleted,
        LocalDate birthDate
) {

    public static FriendDetailResponse from(UserDetailResponse user) {
        return new FriendDetailResponse(
                user.id(), user.nickname(), user.profileImageUrl(), user.tasteAnalysisCompleted(), user.birthDate());
    }
}
