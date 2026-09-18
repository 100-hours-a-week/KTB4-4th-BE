package kr.ktb.zura.needu.auth.dto.response;

import org.springframework.security.web.csrf.CsrfToken;

public record CsrfTokenResponse(String token) {

    public static CsrfTokenResponse from(CsrfToken csrfToken) {
        return new CsrfTokenResponse(csrfToken.getToken());
    }
}
