package kr.ktb.zura.needu.user.dto.response;

import java.time.LocalDate;
import java.util.List;
import kr.ktb.zura.needu.user.entity.User;
import kr.ktb.zura.needu.user.service.GiftPreference;
import kr.ktb.zura.needu.user.type.Gender;

public record UserAiProfileResponse(
        String nickname,
        LocalDate birthDate,
        Gender gender,
        List<String> interestCategoryCodes,
        List<String> allergyCodes,
        List<String> giftExclusionCodes
) {

    public static UserAiProfileResponse from(User user, GiftPreference preference) {
        return new UserAiProfileResponse(
                user.getNickname(),
                user.getBirthDate(),
                user.getGender(),
                preference.interestCategoryCodes(),
                preference.allergyCodes(),
                preference.giftExclusionCodes()
        );
    }
}
