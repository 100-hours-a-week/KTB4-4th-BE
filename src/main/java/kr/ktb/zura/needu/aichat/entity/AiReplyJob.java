package kr.ktb.zura.needu.aichat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;

import kr.ktb.zura.needu.aichat.type.AiReplyJobStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import static lombok.AccessLevel.PROTECTED;

// 행 잠금(FOR UPDATE)으로 상태와 처리 권한 번호(attempt)를 바꾸므로 @Version은 두지 않는다
@Getter
@Entity
@Table(
        name = "ai_reply_jobs",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ai_reply_jobs_user_message_id",
                columnNames = "user_message_id"
        ),
        indexes = {
                @Index(name = "idx_ai_reply_jobs_status_updated_at", columnList = "status, updated_at"),
                @Index(name = "idx_ai_reply_jobs_room_status", columnList = "ai_chat_room_id, status")
        }
)
@NoArgsConstructor(access = PROTECTED)
public class AiReplyJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ai_chat_room_id", nullable = false)
    private AiChatRoom aiChatRoom;

    @Column(nullable = false)
    private Long userMessageId;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiReplyJobStatus status;

    // 작업을 가져갈 때마다 1씩 오르는 처리 권한 번호. 이전 처리의 늦은 저장을 막아야 하므로 내려가지 않는다
    @Column(nullable = false)
    private int attempt;

    @Column(nullable = false, length = 36)
    private String dispatchToken;

    @Column(length = 40)
    private String streamEntryId;

    @Column(nullable = false, length = 36)
    private String lockToken;

    @Column(length = 100)
    private String lastErrorCode;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime finishedAt;

    private AiReplyJob(AiChatRoom aiChatRoom, Long userMessageId, Long userId, String lockToken) {
        this.aiChatRoom = aiChatRoom;
        this.userMessageId = userMessageId;
        this.userId = userId;
        this.status = AiReplyJobStatus.QUEUED;
        this.dispatchToken = newDispatchToken();
        this.lockToken = lockToken;
    }

    public static AiReplyJob create(AiChatRoom aiChatRoom, Long userMessageId, Long userId, String lockToken) {
        return new AiReplyJob(aiChatRoom, userMessageId, userId, lockToken);
    }

    public void take(String streamEntryId) {
        if (isFinished()) {
            throw invalidTransition("take");
        }
        this.status = AiReplyJobStatus.RUNNING;
        this.attempt++;
        this.streamEntryId = streamEntryId;
    }

    public boolean isOwnedBy(int attempt) {
        return status == AiReplyJobStatus.RUNNING && this.attempt == attempt;
    }

    public boolean isRunningWithoutStreamEntry() {
        return status == AiReplyJobStatus.RUNNING && streamEntryId == null;
    }

    public boolean isDispatchedWith(String dispatchToken) {
        return this.dispatchToken.equals(dispatchToken);
    }

    public void succeed(LocalDateTime finishedAt) {
        validateStatus(AiReplyJobStatus.RUNNING, "succeed");
        this.status = AiReplyJobStatus.SUCCEEDED;
        this.finishedAt = finishedAt;
    }

    public void fail(String errorCode, LocalDateTime finishedAt) {
        if (isFinished()) {
            throw invalidTransition("fail");
        }
        this.status = AiReplyJobStatus.FAILED;
        this.lastErrorCode = errorCode;
        this.finishedAt = finishedAt;
    }

    // 처리 권한 번호(attempt)는 이어 가도록
    public void restart(String lockToken) {
        validateStatus(AiReplyJobStatus.FAILED, "restart");
        this.status = AiReplyJobStatus.QUEUED;
        this.dispatchToken = newDispatchToken();
        this.lockToken = lockToken;
        this.finishedAt = null;
    }

    public void redispatch() {
        if (isFinished()) {
            throw invalidTransition("redispatch");
        }
        this.status = AiReplyJobStatus.QUEUED;
        this.dispatchToken = newDispatchToken();
        this.streamEntryId = null;
    }

    public boolean isFinished() {
        return status == AiReplyJobStatus.SUCCEEDED || status == AiReplyJobStatus.FAILED;
    }

    private void validateStatus(AiReplyJobStatus expected, String action) {
        if (status != expected) {
            throw invalidTransition(action);
        }
    }

    private IllegalStateException invalidTransition(String action) {
        return new IllegalStateException(
                "AI reply job cannot " + action + ". jobId=" + id + ", status=" + status);
    }

    private static String newDispatchToken() {
        return UUID.randomUUID().toString();
    }
}
