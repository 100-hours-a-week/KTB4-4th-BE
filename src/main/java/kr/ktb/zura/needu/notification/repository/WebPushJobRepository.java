package kr.ktb.zura.needu.notification.repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import kr.ktb.zura.needu.notification.entity.WebPushJob;
import kr.ktb.zura.needu.notification.type.WebPushJobStatus;
import kr.ktb.zura.needu.notification.type.WebPushQueueType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebPushJobRepository extends JpaRepository<WebPushJob, Long>, WebPushJobRepositoryCustom {

    // 다른 워커가 잠근 행은 건너뛰어 여러 인스턴스가 같은 작업을 동시에 가져가지 않게 한다.
    // 대기열 하나만 조회해 (status, queue_type, available_at) 인덱스 순서대로 읽으므로 따로 정렬하지 않는다
    @Query(value = """
            SELECT id FROM webpush_jobs
            WHERE status = 'QUEUED' AND queue_type = :#{#queueType.name()} AND available_at <= :now
            ORDER BY available_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Long> findAllAvailableIdsForUpdate(
            @Param("queueType") WebPushQueueType queueType,
            @Param("now") LocalDateTime now,
            @Param("limit") int limit
    );

    // 가져간 작업들을 한 문장으로 RUNNING으로 바꾼다. 일괄 UPDATE라 @UpdateTimestamp가 적용되지 않아 updated_at을 직접 넣는다
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update WebPushJob job
            set job.status = kr.ktb.zura.needu.notification.type.WebPushJobStatus.RUNNING,
                job.leaseToken = :leaseToken,
                job.leaseExpiresAt = :leaseExpiresAt,
                job.updatedAt = :now
            where job.id in :ids
              and job.status = kr.ktb.zura.needu.notification.type.WebPushJobStatus.QUEUED
            """)
    int updateToRunning(
            @Param("ids") List<Long> ids,
            @Param("leaseToken") String leaseToken,
            @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt,
            @Param("now") LocalDateTime now
    );

    // 결과를 반영하는 동안 회수 스케줄러가 같은 작업을 되돌리지 못하게 잠근다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from WebPushJob job where job.id = :id and job.leaseToken = :leaseToken")
    Optional<WebPushJob> findByIdAndLeaseTokenForUpdate(@Param("id") Long id, @Param("leaseToken") String leaseToken);

    // 리스가 만료돼 다른 워커가 가져간 작업이면 0을 반환한다
    @Modifying
    @Query("delete from WebPushJob job where job.id = :id and job.leaseToken = :leaseToken")
    int deleteByIdAndLeaseToken(@Param("id") Long id, @Param("leaseToken") String leaseToken);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<WebPushJob> findAllByStatusAndLeaseExpiresAtLessThanEqual(
            WebPushJobStatus status, LocalDateTime leaseExpiresAt, Limit limit);

    @Modifying
    @Query("delete from WebPushJob job where job.status = :status and job.updatedAt < :updatedAt")
    int deleteAllByStatusAndUpdatedAtBefore(
            @Param("status") WebPushJobStatus status, @Param("updatedAt") LocalDateTime updatedAt);
}
