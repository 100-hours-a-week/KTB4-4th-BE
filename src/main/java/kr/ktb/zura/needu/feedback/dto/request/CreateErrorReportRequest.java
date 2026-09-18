package kr.ktb.zura.needu.feedback.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CreateErrorReportRequest(
        @NotNull @Valid ErrorContextRequest errorContext,
        @NotNull @Valid ErrorFeedbackRequest feedback
) {
}
