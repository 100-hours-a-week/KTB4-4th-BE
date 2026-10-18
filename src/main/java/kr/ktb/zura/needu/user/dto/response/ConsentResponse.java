package kr.ktb.zura.needu.user.dto.response;

import java.time.LocalDateTime;
import kr.ktb.zura.needu.user.entity.UserConsent;
import kr.ktb.zura.needu.user.type.ConsentType;

public record ConsentResponse(
        Long id,
        String title,
        String content,
        boolean required,
        String version,
        boolean agreed,
        LocalDateTime agreedAt
) {

    public static ConsentResponse from(ConsentType type, UserConsent latestConsent) {
        boolean agreed = latestConsent != null && latestConsent.isAgreedToCurrentVersion();
        return new ConsentResponse(
                type.getId(),
                type.getTitle(),
                type.getContent(),
                type.isRequired(),
                type.getVersion(),
                agreed,
                agreed ? latestConsent.getCreatedAt() : null
        );
    }
}
