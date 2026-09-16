package kr.ktb.zura.needu.common.security;

import com.jayway.jsonpath.JsonPath;
import kr.ktb.zura.needu.common.exception.ErrorResponseWriter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class RestAuthenticationEntryPointTest {

    private final RestAuthenticationEntryPoint entryPoint =
            new RestAuthenticationEntryPoint(new ErrorResponseWriter(JsonMapper.builder().build()));

    @Test
    void unauthenticatedRequest_commence_writesUnauthorizedResponse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(new MockHttpServletRequest(), response,
                new InsufficientAuthenticationException("unauthenticated"));

        String body = response.getContentAsString();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat((String) JsonPath.read(body, "$.message")).isEqualTo("로그인이 필요합니다.");
        assertThat((Object) JsonPath.read(body, "$.data")).isNull();
    }
}
