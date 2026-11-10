package kr.ktb.zura.needu.anonymouschat.repository;

import java.time.LocalDateTime;
import kr.ktb.zura.needu.anonymouschat.entity.AnonymousReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnonymousReportRepository extends JpaRepository<AnonymousReport, Long> {

    boolean existsByReporterUserIdAndRoomId(Long reporterUserId, Long roomId);

    long countByReportedUserId(Long reportedUserId);

    long countByReportedUserIdAndCreatedAtAfter(Long reportedUserId, LocalDateTime createdAt);
}
