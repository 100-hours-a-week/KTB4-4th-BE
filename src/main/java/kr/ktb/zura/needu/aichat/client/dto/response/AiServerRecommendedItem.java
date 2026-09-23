package kr.ktb.zura.needu.aichat.client.dto.response;

import java.math.BigDecimal;
import kr.ktb.zura.needu.product.type.PlatformType;

public record AiServerRecommendedItem(
        PlatformType platform,
        String externalId,
        BigDecimal score,
        String reason
) {
}
