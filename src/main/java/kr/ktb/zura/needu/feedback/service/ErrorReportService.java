package kr.ktb.zura.needu.feedback.service;

import java.util.UUID;

import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.feedback.dto.request.CreateErrorReportRequest;
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
    public ErrorReportResponse createErrorReport(
            Long userId,
            UUID idempotencyKey,
            CreateErrorReportRequest request
    ) {
        validateDetailLength(request.detail());
        validateNotDuplicated(userId, idempotencyKey);
        ErrorReport errorReport = saveErrorReport(toErrorReport(userId, idempotencyKey, request));

        log.info("Error report created. userId={}, errorReportId={}", userId, errorReport.getId());
        return ErrorReportResponse.from(errorReport);
    }

    // 사용자가 줄일 수 있는 상세 내용 초과는 일반 입력값 오류(422)와 구분해 413으로 안내
    private void validateDetailLength(String detail) {
        if (detail.length() > MAX_DETAIL_LENGTH) {
            throw new BusinessException(FeedbackErrorCode.FEEDBACK_ERROR_REPORT_TOO_LARGE);
        }
    }

    private void validateNotDuplicated(Long userId, UUID idempotencyKey) {
        boolean isDuplicated = errorReportRepository.existsByUserIdAndIdempotencyKey(userId, idempotencyKey);
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

    private ErrorReport toErrorReport(Long userId, UUID idempotencyKey, CreateErrorReportRequest request) {
        return new ErrorReport(
                userId,
                idempotencyKey,
                request.problemType(),
                request.detail()
        );
    }
}
