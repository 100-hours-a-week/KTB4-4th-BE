package kr.ktb.zura.needu.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

import kr.ktb.zura.needu.user.type.ConsentType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import static lombok.AccessLevel.PROTECTED;

@Getter
@Entity
@Table(
        name = "user_consents",
        indexes = @Index(
                name = "idx_user_consents_user_id_consent_type_id",
                columnList = "user_id, consent_type, id"
        )
)
@NoArgsConstructor(access = PROTECTED)
public class UserConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ConsentType consentType;

    // 동의 당시의 항목 버전, ConsentType의 버전이 올라가면 이 값과 달라져 미동의로
    @Column(nullable = false, length = 20)
    private String version;

    @Column(nullable = false)
    private boolean agreed;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private UserConsent(Long userId, ConsentType consentType, boolean agreed) {
        this.userId = userId;
        this.consentType = consentType;
        this.version = consentType.getVersion();
        this.agreed = agreed;
    }

    public static UserConsent create(Long userId, ConsentType consentType, boolean agreed) {
        return new UserConsent(userId, consentType, agreed);
    }

    public boolean isAgreedToCurrentVersion() {
        return agreed && consentType.isCurrentVersion(version);
    }

    public boolean isSameChoiceInCurrentVersion(boolean agreed) {
        return this.agreed == agreed && consentType.isCurrentVersion(version);
    }
}
