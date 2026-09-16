package kr.ktb.zura.needu.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Getter
@Table(name = "user_taste_profiles")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserTasteProfile {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(
                    name = "FK_users_TO_user_taste_profiles_1"
            )
    )
    private User user;

    @Column(name = "recent_tastes", columnDefinition = "TEXT")
    private String recentTastes;

    @Column(name = "recent_interests", columnDefinition = "TEXT")
    private String recentInterests;

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "onboarding_tastes", nullable = false)
    private Map<String, Object> onboardingTastes;

    public UserTasteProfile(User user, Map<String, Object> onboardingTastes) {
        this.user = user;
        this.onboardingTastes = onboardingTastes;
    }
}