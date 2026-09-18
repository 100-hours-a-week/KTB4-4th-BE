package kr.ktb.zura.needu.feedback.repository;

import java.time.LocalDateTime;

import kr.ktb.zura.needu.feedback.entity.ErrorReport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class ErrorReportRepositoryTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 9, 18, 10, 15, 30);

    @Autowired
    private ErrorReportRepository errorReportRepository;

    @Test
    void sameOccurrenceSaved_existsByUserIdAndOccurrence_returnsTrue() {
        errorReportRepository.saveAndFlush(createErrorReport(USER_ID, OCCURRED_AT));

        assertThat(errorReportRepository.existsByUserIdAndOccurrence(USER_ID, "1232", "NETWORK", "NU-09", OCCURRED_AT))
                .isTrue();
    }

    @Test
    void otherUserOrOtherTime_existsByUserIdAndOccurrence_returnsFalse() {
        errorReportRepository.saveAndFlush(createErrorReport(USER_ID, OCCURRED_AT));

        assertThat(errorReportRepository.existsByUserIdAndOccurrence(
                OTHER_USER_ID, "1232", "NETWORK", "NU-09", OCCURRED_AT)).isFalse();
        assertThat(errorReportRepository.existsByUserIdAndOccurrence(
                USER_ID, "1232", "NETWORK", "NU-09", OCCURRED_AT.plusSeconds(1))).isFalse();
    }

    @Test
    void sameOccurrenceSavedTwice_saveAndFlush_violatesUniqueConstraint() {
        errorReportRepository.saveAndFlush(createErrorReport(USER_ID, OCCURRED_AT));

        assertThatThrownBy(() -> errorReportRepository.saveAndFlush(createErrorReport(USER_ID, OCCURRED_AT)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private ErrorReport createErrorReport(Long userId, LocalDateTime occurredAt) {
        return new ErrorReport(userId, "1232", "NETWORK", "NU-09", occurredAt, "1.0.0", "SCREEN_NOT_DISPLAYED", "상세 내용");
    }
}
