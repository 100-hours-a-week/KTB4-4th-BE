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
    @Column(nullable = false)
    private Long id;

    @Column
    private Long externalId;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(length = 2048)
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;

    @Column
    private LocalDate birthDate;

    @Column(nullable = false)
    private boolean onboardingCompleted = true; // V2 온보딩 추가 기준, true -> false 변경 필요

    @Column(nullable = false)
    private boolean tasteAnalysisCompleted = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserStatus status = UserStatus.ACTIVE; // V2 온보딩 추가 기준, ACTIVE -> ONBOARDING 변경 필요

    @Column
    private LocalDateTime blockedAt;

    @Column
    private LocalDateTime lastLoginAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column
    private LocalDateTime deletedAt;

    public User(Long externalId, String nickname, String profileImageUrl, Gender gender, LocalDate birthDate) {
        this.externalId = externalId;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.gender = gender;
        this.birthDate = birthDate;
    }

    public void completeOnboarding() {
        this.onboardingCompleted = true;
        this.status = UserStatus.ACTIVE;
    }

    public void completeTasteAnalysis() {
        this.tasteAnalysisCompleted = true;
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
