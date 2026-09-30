package kr.ktb.zura.needu.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.common.exception.ErrorResponseWriter;
import kr.ktb.zura.needu.common.response.CsrfRefreshResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ErrorResponseWriter errorResponseWriter;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        // FE가 토큰 재발급으로 복구할 수 있도록 일반 403과 구분
        if (accessDeniedException instanceof CsrfException) {
            log.info("CSRF token rejected. type={}, method={}, path={}",
                    accessDeniedException.getClass().getSimpleName(), request.getMethod(), request.getRequestURI());
            errorResponseWriter.write(response, CommonErrorCode.COMMON_CSRF_TOKEN_INVALID,
                    new CsrfRefreshResponse(true));
            return;
        }
        errorResponseWriter.write(response, CommonErrorCode.COMMON_FORBIDDEN);
    }
}
