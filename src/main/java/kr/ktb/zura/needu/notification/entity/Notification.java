package kr.ktb.zura.needu.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import static lombok.AccessLevel.PROTECTED;

@Getter
@Entity
@Table(
        name = "notifications",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notifications_event_receiver",
                columnNames = {"notification_event_id", "receiver_user_id"}
        ),
        indexes = {
                @Index(name = "idx_notifications_receiver_user_id_id", columnList = "receiver_user_id, id"),
                @Index(name = "idx_notifications_receiver_user_id_read_at", columnList = "receiver_user_id, read_at")
        }
)
@NoArgsConstructor(access = PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_event_id", nullable = false)
    private NotificationEvent event;

    @Column(nullable = false)
    private Long receiverUserId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime readAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime deletedAt;

    private Notification(NotificationEvent event, Long receiverUserId, LocalDateTime expiresAt) {
        this.event = event;
        this.receiverUserId = receiverUserId;
        this.expiresAt = expiresAt;
    }

    public static Notification create(NotificationEvent event, Long receiverUserId, LocalDateTime expiresAt) {
        return new Notification(event, receiverUserId, expiresAt);
    }

    // 이미 읽은 알림은 처음 읽은 시각을 유지한다(멱등)
    public void read() {
        if (readAt == null) {
            readAt = LocalDateTime.now();
        }
    }

    public void delete() {
        if (deletedAt == null) {
            deletedAt = LocalDateTime.now();
        }
    }

    public boolean isRead() {
        return readAt != null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
