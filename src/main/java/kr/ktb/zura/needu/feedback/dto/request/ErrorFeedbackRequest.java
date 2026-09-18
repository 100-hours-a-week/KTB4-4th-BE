package kr.ktb.zura.needu.feedback.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// detail 길이 초과의 경우, 413으로 응답해야 하기에 BeanValidation 제거
public record ErrorFeedbackRequest(
        @NotBlank @Size(max = MAX_PROBLEM_TYPE_LENGTH) String problemType,
        @NotBlank String detail
) {

    private static final int MAX_PROBLEM_TYPE_LENGTH = 50;
}
