package kr.ktb.zura.needu.user.entity;

import jakarta.persistence.*;
import kr.ktb.zura.needu.user.type.Gender;
import kr.ktb.zura.needu.user.type.UserStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Getter
@Table(name = "users")
@NoArgsConstructor(access = PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "external_id")
    private Long externalId;

    @Column(name = "nickname", nullable = false, length = 50)
    private String nickname;

    @Column(name = "profile_image_url", length = 2048)
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false)
    private Gender gender;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "onboarding_completed", nullable = false)
    private boolean onboardingCompleted = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private UserStatus status = UserStatus.ONBOARDING;

    @Column(name = "blocked_at")
    private LocalDateTime blockedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public User(Long externalId, String nickname, String profileImageUrl, Gender gender, LocalDate birthDate) {
        this.externalId = externalId;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.gender = gender;
        this.birthDate = birthDate;
        this.onboardingCompleted = false;
    }

    public void completeOnboarding() {
        this.onboardingCompleted = true;
        this.status = UserStatus.ACTIVE;
    }

    public void recordLogin() {
        this.lastLoginAt = LocalDateTime.now();
    }

    public void block() {
        this.status = UserStatus.BLOCKED;
        this.blockedAt = LocalDateTime.now();
    }

    public void withdraw() {
        this.status = UserStatus.WITHDRAWN;
        this.deletedAt = LocalDateTime.now();
    }
}
