package kr.ktb.zura.needu.auth.entity;

import jakarta.persistence.*;
import kr.ktb.zura.needu.auth.type.RevokeReason;
import kr.ktb.zura.needu.user.entity.User;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "auth_sessions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "refresh_token_hash", nullable = false, unique = true, length = 32)
    private byte[] refreshTokenHash;

    @Column(name = "device_name", length = 100)
    private String deviceName;

    @Column(name = "ip_address", length = 16)
    private byte[] ipAddress;

    @Column(name = "refresh_expires_at", nullable = false)
    private LocalDateTime refreshExpiresAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revoke_reason")
    private RevokeReason revokeReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
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