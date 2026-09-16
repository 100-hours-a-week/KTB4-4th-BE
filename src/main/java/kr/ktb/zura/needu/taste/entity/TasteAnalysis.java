package kr.ktb.zura.needu.taste.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

import kr.ktb.zura.needu.taste.type.TasteAnalysisDecision;
import kr.ktb.zura.needu.taste.type.TasteAnalysisStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import static lombok.AccessLevel.PROTECTED;

@Getter
@Entity
@Table(name = "taste_analyses")
@NoArgsConstructor(access = PROTECTED)
public class TasteAnalysis {

    @Id
    private Long roomId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TasteAnalysisStatus status = TasteAnalysisStatus.QUEUED;

    @Column(nullable = false)
    private int progress;

    @Column(length = 255)
    private String progressMessage;

    @Column(columnDefinition = "TEXT")
    private String candidateResult;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TasteAnalysisDecision decision = TasteAnalysisDecision.PENDING;

    private LocalDateTime completedAt;

    private LocalDateTime decidedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
