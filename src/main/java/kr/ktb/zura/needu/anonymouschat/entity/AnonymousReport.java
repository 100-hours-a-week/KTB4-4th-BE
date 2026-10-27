package kr.ktb.zura.needu.anonymouschat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import kr.ktb.zura.needu.anonymouschat.type.AnonymousReportReason;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import static lombok.AccessLevel.PROTECTED;

@Getter
@Entity
@Table(name = "anonymous_reports",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_anonymous_reports_reporter_room",
                columnNames = {"reporter_user_id", "room_id"}
        ),
        indexes = @Index(name = "idx_anonymous_reports_reported_user_id", columnList = "reported_user_id")
)
@NoArgsConstructor(access = PROTECTED)
public class AnonymousReport {

    public static final int DESCRIPTION_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long reporterUserId;

    @Column(nullable = false)
    private Long reportedUserId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_anonymous_reports_room"))
    private AnonymousRoom room;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnonymousReportReason reason;

    @Column(length = DESCRIPTION_MAX_LENGTH)
    private String description;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public AnonymousReport(Long reporterUserId, AnonymousRoom room, AnonymousReportReason reason,
                           String description) {
        this.reporterUserId = reporterUserId;
        this.reportedUserId = room.isQuestioner(reporterUserId)
                ? room.getRecipientUserId()
                : room.getQuestionerUserId();
        this.room = room;
        this.reason = reason;
        this.description = description;
    }
}
