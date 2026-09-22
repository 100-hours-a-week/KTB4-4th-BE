package kr.ktb.zura.needu.feedback.dto.response;

import kr.ktb.zura.needu.feedback.entity.ErrorReport;

public record ErrorReportResponse(Long errorReportId) {

    public static ErrorReportResponse from(ErrorReport errorReport) {
        return new ErrorReportResponse(errorReport.getId());
    }
}
