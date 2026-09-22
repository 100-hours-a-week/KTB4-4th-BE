package kr.ktb.zura.needu.feedback.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateErrorReportRequest(
        @NotBlank @Size(max = MAX_PROBLEM_TYPE_LENGTH) String problemType,
        @NotBlank String detail
) {

    private static final int MAX_PROBLEM_TYPE_LENGTH = 50;
}
