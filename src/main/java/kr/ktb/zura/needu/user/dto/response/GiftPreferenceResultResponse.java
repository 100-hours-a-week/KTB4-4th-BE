package kr.ktb.zura.needu.user.dto.response;

import java.util.List;
import kr.ktb.zura.needu.user.service.GiftPreference;

public record GiftPreferenceResultResponse(
        List<String> interestCategoryCodes,
        List<String> allergyCodes,
        List<String> giftExclusionCodes
) {

    public static GiftPreferenceResultResponse from(GiftPreference preference) {
        return new GiftPreferenceResultResponse(
                preference.interestCategoryCodes(),
                preference.allergyCodes(),
                preference.giftExclusionCodes()
        );
    }
}
