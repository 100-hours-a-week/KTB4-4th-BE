package kr.ktb.zura.needu.common.security;

import com.jayway.jsonpath.JsonPath;
import kr.ktb.zura.needu.common.exception.ErrorResponseWriter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class RestAccessDeniedHandlerTest {

    private final RestAccessDeniedHandler accessDeniedHandler =
            new RestAccessDeniedHandler(new ErrorResponseWriter(JsonMapper.builder().build()));

    @Test
    void unauthorizedAccess_handle_writesForbiddenResponse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        accessDeniedHandler.handle(new MockHttpServletRequest(), response, new AccessDeniedException("denied"));

        String body = response.getContentAsString();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat((String) JsonPath.read(body, "$.message")).isEqualTo("접근 권한이 없습니다.");
        assertThat((Object) JsonPath.read(body, "$.data")).isNull();
    }

    @Test
    void invalidCsrfToken_handle_writesCsrfRefreshRequiredResponse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        accessDeniedHandler.handle(new MockHttpServletRequest(), response,
                new InvalidCsrfTokenException(new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "expected"), "actual"));

        String body = response.getContentAsString();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat((String) JsonPath.read(body, "$.message")).isEqualTo("보안 토큰이 만료되었습니다. 다시 시도해 주세요.");
        assertThat((Boolean) JsonPath.read(body, "$.data.csrfTokenRefreshRequired")).isTrue();
    }

    @Test
    void missingCsrfToken_handle_writesCsrfRefreshRequiredResponse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        accessDeniedHandler.handle(new MockHttpServletRequest(), response, new MissingCsrfTokenException(null));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat((Boolean) JsonPath.read(response.getContentAsString(), "$.data.csrfTokenRefreshRequired")).isTrue();
    }
}
