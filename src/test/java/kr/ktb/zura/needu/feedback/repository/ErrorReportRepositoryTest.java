package kr.ktb.zura.needu.feedback.repository;

import java.util.UUID;

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
    private static final UUID IDEMPOTENCY_KEY = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_IDEMPOTENCY_KEY = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private ErrorReportRepository errorReportRepository;

    @Test
    void sameIdempotencyKeySaved_existsByUserIdAndIdempotencyKey_returnsTrue() {
        errorReportRepository.saveAndFlush(createErrorReport(USER_ID, IDEMPOTENCY_KEY));

        assertThat(errorReportRepository.existsByUserIdAndIdempotencyKey(USER_ID, IDEMPOTENCY_KEY))
                .isTrue();
    }

    @Test
    void otherUserOrOtherKey_existsByUserIdAndIdempotencyKey_returnsFalse() {
        errorReportRepository.saveAndFlush(createErrorReport(USER_ID, IDEMPOTENCY_KEY));

        assertThat(errorReportRepository.existsByUserIdAndIdempotencyKey(OTHER_USER_ID, IDEMPOTENCY_KEY)).isFalse();
        assertThat(errorReportRepository.existsByUserIdAndIdempotencyKey(USER_ID, OTHER_IDEMPOTENCY_KEY)).isFalse();
    }

    @Test
    void sameUserAndIdempotencyKeySavedTwice_saveAndFlush_violatesUniqueConstraint() {
        errorReportRepository.saveAndFlush(createErrorReport(USER_ID, IDEMPOTENCY_KEY));

        assertThatThrownBy(() -> errorReportRepository.saveAndFlush(createErrorReport(USER_ID, IDEMPOTENCY_KEY)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameIdempotencyKeyForDifferentUsers_saveAndFlush_savesBothReports() {
        errorReportRepository.saveAndFlush(createErrorReport(USER_ID, IDEMPOTENCY_KEY));
        errorReportRepository.saveAndFlush(createErrorReport(OTHER_USER_ID, IDEMPOTENCY_KEY));

        assertThat(errorReportRepository.count()).isEqualTo(2);
    }

    private ErrorReport createErrorReport(Long userId, UUID idempotencyKey) {
        return new ErrorReport(userId, idempotencyKey, "SCREEN_NOT_DISPLAYED", "상세 내용");
    }
}
