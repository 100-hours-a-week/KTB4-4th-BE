package kr.ktb.zura.needu.friend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Getter
@Table(name = "friend_sync_requests")
@NoArgsConstructor(access = PROTECTED)
public class FriendSyncRequest {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    public FriendSyncRequest(Long userId, LocalDateTime requestedAt) {
        this.userId = userId;
        this.requestedAt = requestedAt;
        this.nextAttemptAt = requestedAt;
    }

    public void retryAt(LocalDateTime nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
        this.retryCount++;
    }
}
