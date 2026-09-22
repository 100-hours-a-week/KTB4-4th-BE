package kr.ktb.zura.needu.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kr.ktb.zura.needu.auth.type.RevokeReason;
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

    @Column(name = "user_id", nullable = false)
    private Long userId;

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

    public AuthSession(Long userId, byte[] refreshTokenHash, LocalDateTime refreshExpiresAt) {
        this.userId = userId;
        this.refreshTokenHash = refreshTokenHash;
        this.refreshExpiresAt = refreshExpiresAt;
    }

    public void rotateRefreshToken(byte[] refreshTokenHash) {
        this.refreshTokenHash = refreshTokenHash;
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
        return !this.refreshExpiresAt.isAfter(LocalDateTime.now());
    }
}
