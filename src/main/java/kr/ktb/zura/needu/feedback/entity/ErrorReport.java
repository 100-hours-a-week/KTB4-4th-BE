package kr.ktb.zura.needu.feedback.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import static lombok.AccessLevel.PROTECTED;

@Getter
@Entity
@Table(
        name = "error_reports",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_error_reports_user_id_idempotency_key",
                columnNames = {"user_id", "idempotency_key"}
        )
)
@NoArgsConstructor(access = PROTECTED)
public class ErrorReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 36, updatable = false)
    private UUID idempotencyKey;

    @Column(nullable = false, length = 50)
    private String problemType;

    @Column(nullable = false, length = 500)
    private String detail;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public ErrorReport(Long userId, UUID idempotencyKey, String problemType, String detail) {
        this.userId = userId;
        this.idempotencyKey = idempotencyKey;
        this.problemType = problemType;
        this.detail = detail;
    }
}
