package kr.ktb.zura.needu.aichat.client.dto.request;

import java.util.List;

import kr.ktb.zura.needu.product.dto.response.ProductActivityResponse;

public record AiServerFeedbackRequest(List<AiServerFeedbackItemRequest> items) {

    public static AiServerFeedbackRequest from(List<ProductActivityResponse> activities) {
        return new AiServerFeedbackRequest(activities.stream()
                .map(AiServerFeedbackItemRequest::from)
                .toList());
    }
}
