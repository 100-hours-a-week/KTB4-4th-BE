package kr.ktb.zura.needu.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import static lombok.AccessLevel.PROTECTED;

// 사용자당 한 행. 저장한 적 없는 사용자는 행이 없고 Service가 기본값을 내려준다
@Getter
@Entity
@Table(name = "notification_settings")
@NoArgsConstructor(access = PROTECTED)
public class NotificationSetting {

    private static final boolean DEFAULT_FRIEND_JOINED = true;
    private static final boolean DEFAULT_FRIEND_BIRTHDAY = true;
    private static final boolean DEFAULT_ANNIVERSARY_EVENT = true;
    private static final boolean DEFAULT_MARKETING = false;

    @Id
    private Long userId;

    @Column(nullable = false)
    private boolean friendJoined;

    @Column(nullable = false)
    private boolean friendBirthday;

    @Column(nullable = false)
    private boolean anniversaryEvent;

    @Column(nullable = false)
    private boolean marketing;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private NotificationSetting(Long userId) {
        this.userId = userId;
        this.friendJoined = DEFAULT_FRIEND_JOINED;
        this.friendBirthday = DEFAULT_FRIEND_BIRTHDAY;
        this.anniversaryEvent = DEFAULT_ANNIVERSARY_EVENT;
        this.marketing = DEFAULT_MARKETING;
    }

    public static NotificationSetting createDefault(Long userId) {
        return new NotificationSetting(userId);
    }

    // PATCH 의미에 맞춰 null인 값은 바꾸지 않는다
    public void update(Boolean friendJoined, Boolean friendBirthday, Boolean anniversaryEvent, Boolean marketing) {
        if (friendJoined != null) {
            this.friendJoined = friendJoined;
        }
        if (friendBirthday != null) {
            this.friendBirthday = friendBirthday;
        }
        if (anniversaryEvent != null) {
            this.anniversaryEvent = anniversaryEvent;
        }
        if (marketing != null) {
            this.marketing = marketing;
        }
    }
}
