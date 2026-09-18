package kr.ktb.zura.needu.feedback.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.feedback.dto.request.CreateErrorReportRequest;
import kr.ktb.zura.needu.feedback.dto.request.ErrorContextRequest;
import kr.ktb.zura.needu.feedback.dto.request.ErrorFeedbackRequest;
import kr.ktb.zura.needu.feedback.dto.response.ErrorReportResponse;
import kr.ktb.zura.needu.feedback.entity.ErrorReport;
import kr.ktb.zura.needu.feedback.exception.FeedbackErrorCode;
import kr.ktb.zura.needu.feedback.repository.ErrorReportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ErrorReportServiceTest {

    private static final Long USER_ID = 1L;
    private static final OffsetDateTime OCCURRED_AT = OffsetDateTime.of(2026, 8, 26, 19, 40, 0, 0, ZoneOffset.ofHours(9));
    private static final LocalDateTime LOCAL_OCCURRED_AT =
            OCCURRED_AT.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();

    @Mock
    private ErrorReportRepository errorReportRepository;

    @InjectMocks
    private ErrorReportService errorReportService;

    @Test
    void newErrorReport_createErrorReport_savesReportAndReturnsId() {
        givenDuplicated(false);
        given(errorReportRepository.saveAndFlush(any(ErrorReport.class))).willAnswer(invocation -> {
            ErrorReport errorReport = invocation.getArgument(0);
            // ID는 DB에서 생성되므로 단위 테스트에서만 직접 설정한다.
            ReflectionTestUtils.setField(errorReport, "id", 101L);
            return errorReport;
        });

        ErrorReportResponse response = errorReportService.createErrorReport(USER_ID, createRequest(OCCURRED_AT));

        assertThat(response.errorReportId()).isEqualTo(101L);
        ArgumentCaptor<ErrorReport> captor = ArgumentCaptor.forClass(ErrorReport.class);
        verify(errorReportRepository).saveAndFlush(captor.capture());
        ErrorReport saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getErrorType()).isEqualTo("NETWORK");
        assertThat(saved.getErrorCode()).isEqualTo("NETWORK_DISCONNECTED");
        assertThat(saved.getScreenId()).isEqualTo("NU-11");
        assertThat(saved.getOccurredAt()).isEqualTo(LOCAL_OCCURRED_AT);
        assertThat(saved.getAppVersion()).isEqualTo("1.0.0");
        assertThat(saved.getProblemType()).isEqualTo("SCREEN_NOT_DISPLAYED");
        assertThat(saved.getDetail()).isEqualTo("추천 목록이 열리지 않아요.");
    }

    @Test
    void detailOver500Characters_createErrorReport_throwsTooLargeWithoutQueryingRepository() {
        CreateErrorReportRequest request = createRequest(OCCURRED_AT, "가".repeat(501));

        assertThatThrownBy(() -> errorReportService.createErrorReport(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(FeedbackErrorCode.FEEDBACK_ERROR_REPORT_TOO_LARGE);
        verifyNoInteractions(errorReportRepository);
    }

    @Test
    void detailExactly500Characters_createErrorReport_savesReport() {
        givenDuplicated(false);
        given(errorReportRepository.saveAndFlush(any(ErrorReport.class))).willAnswer(invocation -> invocation.getArgument(0));

        errorReportService.createErrorReport(USER_ID, createRequest(OCCURRED_AT, "가".repeat(500)));

        verify(errorReportRepository).saveAndFlush(any(ErrorReport.class));
    }

    @Test
    void sameInstantWithDifferentOffset_createErrorReport_checksDuplicateAtSameLocalTime() {
        givenDuplicated(true);
        OffsetDateTime sameInstantInUtc = OCCURRED_AT.withOffsetSameInstant(ZoneOffset.UTC);

        assertThatThrownBy(() -> errorReportService.createErrorReport(USER_ID, createRequest(sameInstantInUtc)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(FeedbackErrorCode.FEEDBACK_ERROR_REPORT_DUPLICATED);
    }

    @Test
    void alreadySentErrorReport_createErrorReport_throwsDuplicatedWithoutSaving() {
        givenDuplicated(true);

        assertThatThrownBy(() -> errorReportService.createErrorReport(USER_ID, createRequest(OCCURRED_AT)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(FeedbackErrorCode.FEEDBACK_ERROR_REPORT_DUPLICATED);
        verify(errorReportRepository, never()).saveAndFlush(any());
    }

    @Test
    void concurrentDuplicateViolatesUniqueConstraint_createErrorReport_throwsDuplicated() {
        givenDuplicated(false);
        given(errorReportRepository.saveAndFlush(any(ErrorReport.class)))
                .willThrow(new DataIntegrityViolationException("uk_error_reports_user_id_occurrence"));

        assertThatThrownBy(() -> errorReportService.createErrorReport(USER_ID, createRequest(OCCURRED_AT)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(FeedbackErrorCode.FEEDBACK_ERROR_REPORT_DUPLICATED);
    }

    private void givenDuplicated(boolean duplicated) {
        given(errorReportRepository.existsByUserIdAndOccurrence(
                USER_ID, "NETWORK_DISCONNECTED", "NETWORK", "NU-11", LOCAL_OCCURRED_AT))
                .willReturn(duplicated);
    }

    private CreateErrorReportRequest createRequest(OffsetDateTime occurredAt) {
        return createRequest(occurredAt, "추천 목록이 열리지 않아요.");
    }

    private CreateErrorReportRequest createRequest(OffsetDateTime occurredAt, String detail) {
        return new CreateErrorReportRequest(
                new ErrorContextRequest("NETWORK", "NETWORK_DISCONNECTED", "NU-11", occurredAt, "1.0.0"),
                new ErrorFeedbackRequest("SCREEN_NOT_DISPLAYED", detail)
        );
    }
}
