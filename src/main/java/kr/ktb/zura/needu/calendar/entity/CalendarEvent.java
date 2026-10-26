package kr.ktb.zura.needu.calendar.entity;

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

import kr.ktb.zura.needu.calendar.type.CalendarEventColor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import static lombok.AccessLevel.PROTECTED;

@Getter
@Entity
@Table(
        name = "calendar_events",
        indexes = @Index(name = "idx_calendar_events_user_id_start_at", columnList = "user_id, start_at")
)
@NoArgsConstructor(access = PROTECTED)
public class CalendarEvent {

    public static final int TITLE_MAX_LENGTH = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = TITLE_MAX_LENGTH)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    @Column(nullable = false)
    private boolean allDay;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CalendarEventColor color;

    // true면 일정 7일, 3일, 1일 전에 알림을 보낸다
    @Column(nullable = false)
    private boolean notificationEnabled;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private CalendarEvent(Long userId, String title, String description, LocalDateTime startAt,
                          LocalDateTime endAt, boolean allDay, CalendarEventColor color,
                          boolean notificationEnabled) {
        this.userId = userId;
        this.title = title;
        this.description = description;
        this.startAt = startAt;
        this.endAt = endAt;
        this.allDay = allDay;
        this.color = color;
        this.notificationEnabled = notificationEnabled;
    }

    public static CalendarEvent create(Long userId, String title, String description, LocalDateTime startAt,
                                       LocalDateTime endAt, boolean allDay, CalendarEventColor color,
                                       boolean notificationEnabled) {
        return new CalendarEvent(userId, title, description, startAt, endAt, allDay, color, notificationEnabled);
    }

    public void update(String title, String description, LocalDateTime startAt, LocalDateTime endAt,
                       Boolean allDay, CalendarEventColor color, Boolean notificationEnabled) {
        this.title = keepIfNull(title, this.title);
        this.description = keepIfNull(description, this.description);
        this.startAt = keepIfNull(startAt, this.startAt);
        this.endAt = keepIfNull(endAt, this.endAt);
        this.allDay = keepIfNull(allDay, this.allDay);
        this.color = keepIfNull(color, this.color);
        this.notificationEnabled = keepIfNull(notificationEnabled, this.notificationEnabled);
    }

    private static <T> T keepIfNull(T newValue, T currentValue) {
        return newValue != null ? newValue : currentValue;
    }
}
