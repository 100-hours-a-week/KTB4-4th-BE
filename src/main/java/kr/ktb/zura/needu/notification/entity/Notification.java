package kr.ktb.zura.needu.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

import kr.ktb.zura.needu.notification.type.NotificationCategory;
import kr.ktb.zura.needu.notification.type.NotificationTargetType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import static lombok.AccessLevel.PROTECTED;

@Getter
@Entity
@Table(
        name = "notifications",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notifications_dedup_key",
                columnNames = "dedup_key"
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

    @Column(nullable = false)
    private Long receiverUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationCategory category;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 255)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private NotificationTargetType targetType;

    // 대상 종류마다 ID 체계가 달라 응답 형식(String)과 같게 저장한다
    @Column(length = 50)
    private String targetId;

    // 스케줄러 재실행 등으로 같은 알림이 두 번 만들어지지 않도록 막는다. 중복 걱정이 없는 알림은 null
    @Column(length = 100)
    private String dedupKey;

    private LocalDateTime readAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 삭제한 알림도 dedupKey를 유지해야 같은 알림이 다시 만들어지지 않아 soft delete로 처리한다
    private LocalDateTime deletedAt;

    private Notification(Long receiverUserId, NotificationCategory category, String title, String body,
                         NotificationTargetType targetType, String targetId, String dedupKey) {
        this.receiverUserId = receiverUserId;
        this.category = category;
        this.title = title;
        this.body = body;
        this.targetType = targetType;
        this.targetId = targetId;
        this.dedupKey = dedupKey;
    }

    public static Notification create(Long receiverUserId, NotificationCategory category, String title, String body,
                                      NotificationTargetType targetType, String targetId, String dedupKey) {
        return new Notification(receiverUserId, category, title, body, targetType, targetId, dedupKey);
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
