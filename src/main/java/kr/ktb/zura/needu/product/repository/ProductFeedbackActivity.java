package kr.ktb.zura.needu.product.repository;

import java.time.LocalDateTime;

import kr.ktb.zura.needu.product.type.PlatformType;
import kr.ktb.zura.needu.product.type.ProductContext;
import kr.ktb.zura.needu.product.type.ProductFeedbackType;

public record ProductFeedbackActivity(
        PlatformType platformType,
        String externalId,
        ProductFeedbackType feedbackType,
        ProductContext context,
        LocalDateTime occurredAt
) {
}
