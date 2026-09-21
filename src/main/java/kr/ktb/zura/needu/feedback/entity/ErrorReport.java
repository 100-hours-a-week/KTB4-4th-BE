package kr.ktb.zura.needu.feedback.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import static lombok.AccessLevel.PROTECTED;

@Getter
@Entity
@Table(
        name = "error_reports",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_error_reports_user_id_occurrence",
                columnNames = {"user_id", "error_code", "error_type", "occurred_at"}
        )
)
@NoArgsConstructor(access = PROTECTED)
public class ErrorReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 50)
    private String errorCode;

    @Column(nullable = false, length = 30)
    private String errorType;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    @Column(nullable = false, length = 30)
    private String appVersion;

    @Column(nullable = false, length = 50)
    private String problemType;

    @Column(nullable = false, length = 500)
    private String detail;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public ErrorReport(Long userId, String errorCode, String errorType, LocalDateTime occurredAt,
                       String appVersion, String problemType, String detail) {
        this.userId = userId;
        this.errorCode = errorCode;
        this.errorType = errorType;
        this.occurredAt = occurredAt;
        this.appVersion = appVersion;
        this.problemType = problemType;
        this.detail = detail;
    }
}
