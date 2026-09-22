package kr.ktb.zura.needu.feedback.exception;

import kr.ktb.zura.needu.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FeedbackErrorCode implements ErrorCode {

    FEEDBACK_ERROR_REPORT_DUPLICATED(HttpStatus.CONFLICT, "이미 전송한 피드백입니다."),
    FEEDBACK_ERROR_REPORT_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "피드백 내용이 너무 큽니다.");

    private final HttpStatus status;
    private final String message;
}
