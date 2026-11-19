package kr.ktb.zura.needu.notification.entity;

import static lombok.AccessLevel.PROTECTED;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;
import kr.ktb.zura.needu.notification.type.WebPushJobStatus;
import kr.ktb.zura.needu.notification.type.WebPushQueueType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(
        name = "webpush_jobs",
        indexes = {
                @Index(name = "idx_webpush_jobs_status_queue_type_available_at",
                        columnList = "status, queue_type, available_at"),
                @Index(name = "idx_webpush_jobs_status_lease_expires_at", columnList = "status, lease_expires_at")
        }
)
@NoArgsConstructor(access = PROTECTED)
public class WebPushJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long notificationEventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WebPushQueueType queueType;

    // 아직 보내지 않은 수신자의 알림 ID. 재시도할 때는 실패한 수신자만 남긴다
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<Long> notificationIds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WebPushJobStatus status;

    @Column(nullable = false)
    private int retryCount;

    @Column(nullable = false)
    private LocalDateTime availableAt;

    @Column(length = 36)
    private String leaseToken;

    private LocalDateTime leaseExpiresAt;

    @Column(length = 100)
    private String lastErrorCode;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private WebPushJob(
            Long notificationEventId,
            WebPushQueueType queueType,
            List<Long> notificationIds,
            LocalDateTime availableAt
    ) {
        this.notificationEventId = notificationEventId;
        this.queueType = queueType;
        this.notificationIds = List.copyOf(notificationIds);
        this.status = WebPushJobStatus.QUEUED;
        this.availableAt = availableAt;
    }

    public static WebPushJob create(
            Long notificationEventId,
            WebPushQueueType queueType,
            List<Long> notificationIds,
            LocalDateTime availableAt
    ) {
        if (queueType == null) {
            throw new IllegalArgumentException("Web push job must have a queue type.");
        }
        validateNotificationIds(notificationIds);
        return new WebPushJob(notificationEventId, queueType, notificationIds, availableAt);
    }

    public boolean isLeasedBy(String leaseToken) {
        return status == WebPushJobStatus.RUNNING && this.leaseToken != null && this.leaseToken.equals(leaseToken);
    }

    /**
     * 발송에 실패한 수신자만 남겨 다시 대기열에 넣는다. 리스가 만료된 작업을 회수할 때도 사용한다.
     *
     * @param remainingNotificationIds 다시 보낼 수신자의 알림 ID
     * @param availableAt 다시 가져갈 수 있는 시각(백오프 반영)
     */
    public void retry(List<Long> remainingNotificationIds, LocalDateTime availableAt, String errorCode) {
        validateStatus(WebPushJobStatus.RUNNING, "retry");
        validateNotificationIds(remainingNotificationIds);
        this.notificationIds = List.copyOf(remainingNotificationIds);
        this.status = WebPushJobStatus.QUEUED;
        this.retryCount++;
        this.availableAt = availableAt;
        this.lastErrorCode = errorCode;
        releaseLease();
    }

    public void fail(String errorCode) {
        validateStatus(WebPushJobStatus.RUNNING, "fail");
        this.status = WebPushJobStatus.FAILED;
        this.lastErrorCode = errorCode;
        releaseLease();
    }

    private void releaseLease() {
        this.leaseToken = null;
        this.leaseExpiresAt = null;
    }

    private void validateStatus(WebPushJobStatus expected, String action) {
        if (status != expected) {
            throw new IllegalStateException(
                    "Web push job cannot " + action + ". jobId=" + id + ", status=" + status);
        }
    }

    private static void validateNotificationIds(List<Long> notificationIds) {
        if (notificationIds == null || notificationIds.isEmpty()) {
            throw new IllegalArgumentException("Web push job must have at least one notification id.");
        }
    }
}
