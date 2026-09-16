package kr.ktb.zura.needu.user.dto.response;

import java.time.LocalDate;

import kr.ktb.zura.needu.user.entity.User;

public record UserSummaryResponse(Long id, String nickname, LocalDate birthDate, boolean tasteAnalysisCompleted) {

    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(
                user.getId(),
                user.getNickname(),
                user.getBirthDate(),
                user.isTasteAnalysisCompleted()
        );
    }
}
