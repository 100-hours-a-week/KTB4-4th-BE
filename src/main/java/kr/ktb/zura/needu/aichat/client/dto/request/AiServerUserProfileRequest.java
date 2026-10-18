package kr.ktb.zura.needu.aichat.client.dto.request;

import java.time.LocalDate;
import java.util.List;
import kr.ktb.zura.needu.user.dto.response.UserAiProfileResponse;
import kr.ktb.zura.needu.user.type.Gender;

public record AiServerUserProfileRequest(
        String nickname,
        LocalDate birthDate,
        Gender gender,
        List<String> interestCategoryCodes,
        List<String> allergyCodes,
        List<String> giftExclusionCodes
) {

    public static AiServerUserProfileRequest from(UserAiProfileResponse profile) {
        return new AiServerUserProfileRequest(
                profile.nickname(),
                profile.birthDate(),
                profile.gender(),
                profile.interestCategoryCodes(),
                profile.allergyCodes(),
                profile.giftExclusionCodes()
        );
    }
}
