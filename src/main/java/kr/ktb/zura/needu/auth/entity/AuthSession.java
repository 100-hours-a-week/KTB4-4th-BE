package kr.ktb.zura.needu.auth.entity;

import jakarta.persistence.*;
import kr.ktb.zura.needu.auth.type.RevokeReason;
import kr.ktb.zura.needu.user.entity.User;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Getter
@Table(name = "auth_sessions")
@NoArgsConstructor(access = PROTECTED)
public class AuthSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true, length = 32)
    private byte[] refreshTokenHash;

    @Column(length = 100)
    private String deviceName;

    @Column(length = 16)
    private byte[] ipAddress;

    @Column(nullable = false)
    private LocalDateTime refreshExpiresAt;

    @Column
    private LocalDateTime lastUsedAt;

    @Column
    private LocalDateTime revokedAt;

    @Enumerated(EnumType.STRING)
    @Column
    private RevokeReason revokeReason;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public AuthSession(User user, byte[] refreshTokenHash, String deviceName, byte[] ipAddress, LocalDateTime refreshExpiresAt) {
        this.user = user;
        this.refreshTokenHash = refreshTokenHash;
        this.deviceName = deviceName;
        this.ipAddress = ipAddress;
        this.refreshExpiresAt = refreshExpiresAt;
    }

    public void recordUse() {
        this.lastUsedAt = LocalDateTime.now();
    }

    public void revoke(RevokeReason reason) {
        this.revokedAt = LocalDateTime.now();
        this.revokeReason = reason;
    }

    public boolean isRevoked() {
        return this.revokedAt != null;
    }

    public boolean isExpired() {
        return this.refreshExpiresAt.isBefore(LocalDateTime.now());
    }
}