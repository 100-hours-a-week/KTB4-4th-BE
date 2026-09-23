package kr.ktb.zura.needu.feedback.service;

import java.util.UUID;

import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.feedback.dto.request.CreateErrorReportRequest;
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
    private static final UUID IDEMPOTENCY_KEY = UUID.fromString("11111111-1111-1111-1111-111111111111");

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

        ErrorReportResponse response = errorReportService.createErrorReport(
                USER_ID, IDEMPOTENCY_KEY, createRequest("추천 목록이 열리지 않아요."));

        assertThat(response.errorReportId()).isEqualTo(101L);
        ArgumentCaptor<ErrorReport> captor = ArgumentCaptor.forClass(ErrorReport.class);
        verify(errorReportRepository).saveAndFlush(captor.capture());
        ErrorReport saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
        assertThat(saved.getProblemType()).isEqualTo("SCREEN_NOT_DISPLAYED");
        assertThat(saved.getDetail()).isEqualTo("추천 목록이 열리지 않아요.");
    }

    @Test
    void detailOver500Characters_createErrorReport_throwsTooLargeWithoutQueryingRepository() {
        CreateErrorReportRequest request = createRequest("가".repeat(501));

        assertThatThrownBy(() -> errorReportService.createErrorReport(USER_ID, IDEMPOTENCY_KEY, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(FeedbackErrorCode.FEEDBACK_ERROR_REPORT_TOO_LARGE);
        verifyNoInteractions(errorReportRepository);
    }

    @Test
    void detailExactly500Characters_createErrorReport_savesReport() {
        givenDuplicated(false);
        given(errorReportRepository.saveAndFlush(any(ErrorReport.class))).willAnswer(invocation -> invocation.getArgument(0));

        errorReportService.createErrorReport(USER_ID, IDEMPOTENCY_KEY, createRequest("가".repeat(500)));

        verify(errorReportRepository).saveAndFlush(any(ErrorReport.class));
    }

    @Test
    void alreadyUsedIdempotencyKey_createErrorReport_throwsDuplicatedWithoutSaving() {
        givenDuplicated(true);

        assertThatThrownBy(() -> errorReportService.createErrorReport(
                USER_ID, IDEMPOTENCY_KEY, createRequest("추천 목록이 열리지 않아요.")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(FeedbackErrorCode.FEEDBACK_ERROR_REPORT_DUPLICATED);
        verify(errorReportRepository, never()).saveAndFlush(any());
    }

    @Test
    void concurrentDuplicateViolatesUniqueConstraint_createErrorReport_throwsDuplicated() {
        givenDuplicated(false);
        given(errorReportRepository.saveAndFlush(any(ErrorReport.class)))
                .willThrow(new DataIntegrityViolationException("uk_error_reports_user_id_idempotency_key"));

        assertThatThrownBy(() -> errorReportService.createErrorReport(
                USER_ID, IDEMPOTENCY_KEY, createRequest("추천 목록이 열리지 않아요.")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(FeedbackErrorCode.FEEDBACK_ERROR_REPORT_DUPLICATED);
    }

    private void givenDuplicated(boolean duplicated) {
        given(errorReportRepository.existsByUserIdAndIdempotencyKey(USER_ID, IDEMPOTENCY_KEY))
                .willReturn(duplicated);
    }

    private CreateErrorReportRequest createRequest(String detail) {
        return new CreateErrorReportRequest("SCREEN_NOT_DISPLAYED", detail);
    }
}
