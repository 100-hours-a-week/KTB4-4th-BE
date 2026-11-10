package kr.ktb.zura.needu.product.dto.response;

import java.time.LocalDateTime;

import kr.ktb.zura.needu.product.repository.ProductFeedbackActivity;
import kr.ktb.zura.needu.product.repository.PurchaseActivity;
import kr.ktb.zura.needu.product.type.PlatformType;
import kr.ktb.zura.needu.product.type.ProductActivityType;
import kr.ktb.zura.needu.product.type.ProductContext;

public record ProductActivityResponse(
        PlatformType platformType,
        String externalId,
        ProductActivityType activityType,
        ProductContext context,
        LocalDateTime occurredAt
) {

    public static ProductActivityResponse from(ProductFeedbackActivity activity) {
        return new ProductActivityResponse(
                activity.platformType(),
                activity.externalId(),
                ProductActivityType.from(activity.feedbackType()),
                activity.context(),
                activity.occurredAt());
    }

    public static ProductActivityResponse from(PurchaseActivity activity) {
        return new ProductActivityResponse(
                activity.platformType(),
                activity.externalId(),
                ProductActivityType.fromPurchaseContext(activity.context()),
                activity.context(),
                activity.occurredAt());
    }
}
