package kr.ktb.zura.needu.feedback.repository;

import java.util.UUID;

import kr.ktb.zura.needu.feedback.entity.ErrorReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ErrorReportRepository extends JpaRepository<ErrorReport, Long> {

    boolean existsByUserIdAndIdempotencyKey(Long userId, UUID idempotencyKey);
}
