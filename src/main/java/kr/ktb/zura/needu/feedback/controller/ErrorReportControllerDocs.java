package kr.ktb.zura.needu.feedback.controller;

import java.util.List;
import kr.ktb.zura.needu.common.openapi.ControllerDocs;
import kr.ktb.zura.needu.common.openapi.OperationDoc;
import kr.ktb.zura.needu.feedback.exception.FeedbackErrorCode;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ErrorReportControllerDocs implements ControllerDocs {

    @Override
    public List<OperationDoc> operations() {
        return List.of(
                OperationDoc.of(ErrorReportController.class, "createErrorReport")
                        .successStatus(HttpStatus.CREATED)
                        .errors(UserErrorCode.USER_BLOCKED, UserErrorCode.USER_WITHDRAWN)
                        .errors(FeedbackErrorCode.FEEDBACK_ERROR_REPORT_DUPLICATED,
                                FeedbackErrorCode.FEEDBACK_ERROR_REPORT_TOO_LARGE)
                        .build()
        );
    }
}
