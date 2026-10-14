package kr.ktb.zura.needu.auth.entity;

import static lombok.AccessLevel.PROTECTED;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Getter
@Table(
        name = "kakao_tokens",
        uniqueConstraints = @UniqueConstraint(name = "uk_kakao_tokens_user_id", columnNames = "user_id")
)
@NoArgsConstructor(access = PROTECTED)
public class KakaoToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 1024)
    private String encryptedAccessToken;

    @Column(nullable = false, length = 1024)
    private String encryptedRefreshToken;

    @Column(nullable = false)
    private LocalDateTime accessTokenExpiresAt;

    @Column(nullable = false)
    private LocalDateTime refreshTokenExpiresAt;

    @Column(nullable = false, length = 1000)
    private String scopes;

    @Version
    @Column(nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public KakaoToken(Long userId,
                      String encryptedAccessToken,
                      String encryptedRefreshToken,
                      LocalDateTime accessTokenExpiresAt,
                      LocalDateTime refreshTokenExpiresAt,
                      String scopes) {
        this.userId = userId;
        update(encryptedAccessToken, encryptedRefreshToken, accessTokenExpiresAt, refreshTokenExpiresAt, scopes);
    }

    public void update(String encryptedAccessToken,
                       String encryptedRefreshToken,
                       LocalDateTime accessTokenExpiresAt,
                       LocalDateTime refreshTokenExpiresAt,
                       String scopes) {
        this.encryptedAccessToken = encryptedAccessToken;
        this.encryptedRefreshToken = encryptedRefreshToken;
        this.accessTokenExpiresAt = accessTokenExpiresAt;
        this.refreshTokenExpiresAt = refreshTokenExpiresAt;
        this.scopes = scopes;
    }
}
