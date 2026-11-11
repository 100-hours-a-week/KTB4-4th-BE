package kr.ktb.zura.needu.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DemoLoginRequest(
        @NotBlank @Size(max = 50) String loginId,
        // BCrypt는 72바이트까지만 비교하므로 그 이상은 받지 않는다.
        @NotBlank @Size(max = 72) String password
) {
}
