package kr.ktb.zura.needu.common.security;

import com.jayway.jsonpath.JsonPath;
import kr.ktb.zura.needu.common.exception.ErrorResponseWriter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
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
}
