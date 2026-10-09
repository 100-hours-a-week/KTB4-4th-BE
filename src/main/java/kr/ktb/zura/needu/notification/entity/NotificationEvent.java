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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import kr.ktb.zura.needu.notification.type.NotificationCategory;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Getter
@Entity
@Table(
        name = "notification_events",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notification_events_dedup_key",
                columnNames = "dedup_key"
        ),
        indexes = {
                @Index(name = "idx_notification_events_type_created_at", columnList = "notification_type, created_at"),
                @Index(name = "idx_notification_events_actor_type_created_at",
                        columnList = "actor_user_id, notification_type, created_at")
        }
)
@NoArgsConstructor(access = PROTECTED)
public class NotificationEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 30)
    private NotificationType type;

    // 시스템이 만든 이벤트에는 발신 사용자가 없다
    private Long actorUserId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "content", nullable = false, length = 500)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private NotificationResourceType resourceType;

    private Long resourceId;

    // 스케줄러 재실행 등으로 같은 사건이 두 번 생성되지 않도록 막는다
    @Column(length = 100)
    private String dedupKey;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private NotificationEvent(NotificationType type, Long actorUserId, String title, String body,
                              NotificationResourceType resourceType, Long resourceId, String dedupKey) {
        this.type = type;
        this.actorUserId = actorUserId;
        this.title = title;
        this.body = body;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.dedupKey = dedupKey;
    }

    public static NotificationEvent create(NotificationType type, Long actorUserId, String title, String body,
                                           NotificationResourceType resourceType, Long resourceId, String dedupKey) {
        return new NotificationEvent(type, actorUserId, title, body, resourceType, resourceId, dedupKey);
    }

    public NotificationCategory getCategory() {
        return type.getCategory();
    }
}
