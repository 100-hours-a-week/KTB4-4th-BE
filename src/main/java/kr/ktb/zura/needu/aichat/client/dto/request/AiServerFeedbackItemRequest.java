package kr.ktb.zura.needu.aichat.client.dto.request;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import kr.ktb.zura.needu.product.dto.response.ProductActivityResponse;
import kr.ktb.zura.needu.product.type.PlatformType;
import kr.ktb.zura.needu.product.type.ProductActivityType;
import kr.ktb.zura.needu.product.type.ProductContext;

public record AiServerFeedbackItemRequest(
        PlatformType platform,
        String externalId,
        ProductActivityType action,
        ProductContext context,
        OffsetDateTime occurredAt
) {

    public static AiServerFeedbackItemRequest from(ProductActivityResponse activity) {
        return new AiServerFeedbackItemRequest(
                activity.platformType(),
                activity.externalId(),
                activity.activityType(),
                activity.context(),
                activity.occurredAt().atOffset(ZoneOffset.UTC));
    }
}
