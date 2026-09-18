package kr.ktb.zura.needu.feedback.repository;

import java.time.LocalDateTime;

import kr.ktb.zura.needu.feedback.entity.ErrorReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ErrorReportRepository extends JpaRepository<ErrorReport, Long> {

    @Query("""
            select count(er) > 0
            from ErrorReport er
            where er.userId = :userId
              and er.errorCode = :errorCode
              and er.errorType = :errorType
              and er.screenId = :screenId
              and er.occurredAt = :occurredAt
            """)
    boolean existsByUserIdAndOccurrence(
            @Param("userId") Long userId,
            @Param("errorCode") String errorCode,
            @Param("errorType") String errorType,
            @Param("screenId") String screenId,
            @Param("occurredAt") LocalDateTime occurredAt
    );
}
