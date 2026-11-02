package kr.ktb.zura.needu.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Getter
@Table(name = "user_taste_profiles")
@NoArgsConstructor(access = PROTECTED)
public class UserTasteProfile {

    @Id
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(columnDefinition = "TEXT")
    private String recentTastes;

    @Column(columnDefinition = "TEXT")
    private String recentInterests;

    @Column(columnDefinition = "TEXT")
    private String aiSummary;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> onboardingTastes;

    // 구조는 AI 서버가 관리, 해석하지 않고 그대로 보관
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> aiMemory;

    public UserTasteProfile(User user, Map<String, Object> onboardingTastes) {
        this.user = user;
        this.onboardingTastes = onboardingTastes;
    }

    public void updateOnboardingTastes(Map<String, Object> onboardingTastes) {
        this.onboardingTastes = onboardingTastes;
    }

    public boolean hasOnboardingTastes() {
        return onboardingTastes != null && !onboardingTastes.isEmpty();
    }

    public void updateAnalysis(String aiSummary, String recentTastes, String recentInterests) {
        this.aiSummary = aiSummary;
        this.recentTastes = recentTastes;
        this.recentInterests = recentInterests;
    }

    public void updateAiMemory(Map<String, Object> aiMemory) {
        this.aiMemory = aiMemory;
    }
}
