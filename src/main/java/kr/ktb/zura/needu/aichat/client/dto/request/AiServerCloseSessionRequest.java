package kr.ktb.zura.needu.aichat.client.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;

public record AiServerCloseSessionRequest(
        Long userId,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        AiServerFeedbackRequest feedback
) {
}
