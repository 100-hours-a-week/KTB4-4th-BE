package kr.ktb.zura.needu.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import static lombok.AccessLevel.PROTECTED;

@Getter
@Entity
@Table(
        name = "push_subscriptions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_push_subscriptions_endpoint",
                columnNames = "endpoint"
        ),
        indexes = @Index(name = "idx_push_subscriptions_user_id", columnList = "user_id")
)
@NoArgsConstructor(access = PROTECTED)
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 1000)
    private String endpoint;

    @Column(nullable = false, length = 255)
    private String p256dh;

    @Column(nullable = false, length = 100)
    private String auth;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public PushSubscription(Long userId, String endpoint, String p256dh, String auth) {
        this.userId = userId;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
    }

    // 같은 브라우저에서 다른 계정으로 다시 로그인하면 endpoint는 같고 사용자만 바뀐다
    public void updateSubscription(Long userId, String p256dh, String auth) {
        this.userId = userId;
        this.p256dh = p256dh;
        this.auth = auth;
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }
}
