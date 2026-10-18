package kr.ktb.zura.needu.product.dto.response;

import kr.ktb.zura.needu.product.type.ProductContext;
import kr.ktb.zura.needu.product.type.ProductFeedbackType;

public record ProductFeedbackResponse(Long productId, ProductContext context, ProductFeedbackType feedback) {
}
