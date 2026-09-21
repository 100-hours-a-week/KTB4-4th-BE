package kr.ktb.zura.needu.feedback.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.feedback.dto.request.CreateErrorReportRequest;
import kr.ktb.zura.needu.feedback.dto.request.ErrorContextRequest;
import kr.ktb.zura.needu.feedback.dto.request.ErrorFeedbackRequest;
import kr.ktb.zura.needu.feedback.dto.response.ErrorReportResponse;
import kr.ktb.zura.needu.feedback.entity.ErrorReport;
import kr.ktb.zura.needu.feedback.exception.FeedbackErrorCode;
import kr.ktb.zura.needu.feedback.repository.ErrorReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ErrorReportService {

    private static final int MAX_DETAIL_LENGTH = 500;

    private final ErrorReportRepository errorReportRepository;

    @Transactional
    public ErrorReportResponse createErrorReport(Long userId, CreateErrorReportRequest request) {
        ErrorContextRequest errorContext = request.errorContext();
        LocalDateTime occurredAt = toLocalDateTime(errorContext.occurredAt());

        validateDetailLength(request.feedback().detail());
        validateNotDuplicated(userId, errorContext, occurredAt);
        ErrorReport errorReport = saveErrorReport(toErrorReport(userId, errorContext, occurredAt, request.feedback()));

        log.info("Error report created. userId={}, errorReportId={}", userId, errorReport.getId());
        return ErrorReportResponse.from(errorReport);
    }

    // 사용자가 줄일 수 있는 상세 내용 초과는 일반 입력값 오류(422)와 구분해 413으로 안내
    private void validateDetailLength(String detail) {
        if (detail.length() > MAX_DETAIL_LENGTH) {
            throw new BusinessException(FeedbackErrorCode.FEEDBACK_ERROR_REPORT_TOO_LARGE);
        }
    }

    private void validateNotDuplicated(Long userId, ErrorContextRequest errorContext, LocalDateTime occurredAt) {
        boolean isDuplicated = errorReportRepository.existsByUserIdAndOccurrence(
                userId, errorContext.errorCode(), errorContext.errorType(), occurredAt);
        if (isDuplicated) {
            throw new BusinessException(FeedbackErrorCode.FEEDBACK_ERROR_REPORT_DUPLICATED);
        }
    }

    // 같은 피드백이 동시에 전송되면 둘 다 중복 확인을 통과할 수 있어 유일 제약 위반도 중복 전송으로 처리
    private ErrorReport saveErrorReport(ErrorReport errorReport) {
        try {
            return errorReportRepository.saveAndFlush(errorReport);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(FeedbackErrorCode.FEEDBACK_ERROR_REPORT_DUPLICATED);
        }
    }

    // 클라이언트 오프셋 시각을 UTC가 아닌 서버 시간대의 LocalDateTime으로, 이중 변환 방지
    private LocalDateTime toLocalDateTime(OffsetDateTime occurredAt) {
        return occurredAt.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    private ErrorReport toErrorReport(Long userId, ErrorContextRequest errorContext, LocalDateTime occurredAt,
                                      ErrorFeedbackRequest feedback) {
        return new ErrorReport(
                userId,
                errorContext.errorCode(),
                errorContext.errorType(),
                occurredAt,
                errorContext.appVersion(),
                feedback.problemType(),
                feedback.detail()
        );
    }
}
