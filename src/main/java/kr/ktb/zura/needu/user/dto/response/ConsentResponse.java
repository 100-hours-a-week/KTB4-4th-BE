package kr.ktb.zura.needu.user.dto.response;

import java.time.LocalDateTime;

public record ConsentResponse(
        Long id,
        String title,
        String content,
        boolean required,
        String version,
        boolean agreed,
        LocalDateTime agreedAt
) {
}
