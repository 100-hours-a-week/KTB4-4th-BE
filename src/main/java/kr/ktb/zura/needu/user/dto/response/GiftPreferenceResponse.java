package kr.ktb.zura.needu.user.dto.response;

import java.util.List;
import kr.ktb.zura.needu.user.service.GiftPreference;

public record GiftPreferenceResponse(
        boolean exists,
        List<String> interestCategoryCodes,
        List<String> allergyCodes,
        List<String> giftExclusionCodes
) {

    public static GiftPreferenceResponse from(GiftPreference preference) {
        return new GiftPreferenceResponse(
                true,
                preference.interestCategoryCodes(),
                preference.allergyCodes(),
                preference.giftExclusionCodes()
        );
    }

    public static GiftPreferenceResponse empty() {
        return new GiftPreferenceResponse(false, List.of(), List.of(), List.of());
    }
}
