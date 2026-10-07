package kr.ktb.zura.needu.user.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record GiftPreferenceResultResponse(
        List<String> interestCategoryCodes,
        List<String> allergyCodes,
        List<String> giftExclusionCodes,
        LocalDateTime updatedAt
) {
}
