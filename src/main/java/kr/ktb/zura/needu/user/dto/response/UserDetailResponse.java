package kr.ktb.zura.needu.user.dto.response;

import java.time.LocalDate;
import kr.ktb.zura.needu.user.entity.User;

public record UserDetailResponse(
        Long id,
        String nickname,
        String profileImageUrl,
        boolean tasteAnalysisCompleted,
        LocalDate birthDate
) {

    public static UserDetailResponse from(User user) {
        return new UserDetailResponse(
                user.getId(), user.getNickname(), user.getProfileImageUrl(),
                user.isTasteAnalysisCompleted(), user.getBirthDate());
    }
}
