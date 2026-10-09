package kr.ktb.zura.needu.notification.entity;

import static lombok.AccessLevel.PROTECTED;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import kr.ktb.zura.needu.notification.type.NotificationSettingType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Entity
@Table(
        name = "notification_settings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notification_settings_user_type",
                columnNames = {"user_id", "setting_type"}
        )
)
@NoArgsConstructor(access = PROTECTED)
public class NotificationSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "setting_type", nullable = false, length = 30)
    private NotificationSettingType type;

    @Column(nullable = false)
    private boolean enabled;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private NotificationSetting(Long userId, NotificationSettingType type, boolean enabled) {
        this.userId = userId;
        this.type = type;
        this.enabled = enabled;
    }

    public static NotificationSetting create(Long userId, NotificationSettingType type, boolean enabled) {
        return new NotificationSetting(userId, type, enabled);
    }

    public void update(boolean enabled) {
        this.enabled = enabled;
    }
}
