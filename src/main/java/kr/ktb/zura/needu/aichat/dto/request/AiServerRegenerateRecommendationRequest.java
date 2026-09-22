package kr.ktb.zura.needu.aichat.dto.request;

import java.util.List;
import kr.ktb.zura.needu.aichat.dto.response.AiServerTasteProfile;

public record AiServerRegenerateRecommendationRequest(
        Long userId,
        String mode,
        AiServerTasteProfile profile,
        List<String> excludeCategories,
        List<Long> excludeProductIds,
        Integer limit
) {
}
