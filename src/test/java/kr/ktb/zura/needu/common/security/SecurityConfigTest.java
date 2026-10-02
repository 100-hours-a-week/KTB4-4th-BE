package kr.ktb.zura.needu.common.security;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import kr.ktb.zura.needu.auth.service.AccessTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "management.endpoints.web.exposure.include=health,metrics,prometheus")
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class SecurityConfigTest {

    private static final String CSRF_COOKIE = "NEEDU_CSRF";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";

    private final MockMvc mockMvc;
    private final AccessTokenService accessTokenService;

    SecurityConfigTest(MockMvc mockMvc, AccessTokenService accessTokenService) {
        this.mockMvc = mockMvc;
        this.accessTokenService = accessTokenService;
    }

    private Cookie accessCookie() {
        return new Cookie(AuthCookieNames.ACCESS_TOKEN, accessTokenService.issueAccessToken(1L));
    }

    @Test
    void noAuthentication_findHealth_returnsOk() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void invalidAccessCookie_findHealth_returnsOk() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .cookie(new Cookie(AuthCookieNames.ACCESS_TOKEN, "malformed")))
                .andExpect(status().isOk());
    }

    @Test
    void noAuthentication_findHealthDetails_doesNotExposeComponents() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void missingCsrfToken_postRequest_returnsCsrfRefreshRequired() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("보안 토큰이 만료되었습니다. 다시 시도해 주세요."))
                .andExpect(jsonPath("$.data.csrfTokenRefreshRequired").value(true));
    }

    @Test
    void authenticatedRequest_withCsrfCookie_keepsCsrfCookie() throws Exception {
        Cookie csrfCookie = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andReturn().getResponse().getCookie(CSRF_COOKIE);

        MvcResult result = mockMvc.perform(get("/api/v1/guidance").cookie(accessCookie(), csrfCookie))
                .andReturn();

        assertThat(result.getResponse().getCookie(CSRF_COOKIE)).isNull();
    }

    @Test
    void authenticatedPostRequest_withCsrfCookie_keepsCsrfCookie() throws Exception {
        MvcResult csrf = mockMvc.perform(get("/api/v1/auth/csrf")).andReturn();
        Cookie csrfCookie = csrf.getResponse().getCookie(CSRF_COOKIE);
        String csrfToken = JsonPath.read(csrf.getResponse().getContentAsString(), "$.data.token");

        MvcResult result = mockMvc.perform(post("/api/v1/error-reports")
                        .cookie(accessCookie(), csrfCookie)
                        .header(CSRF_HEADER, csrfToken))
                .andReturn();

        assertThat(result.getResponse().getCookie(CSRF_COOKIE)).isNull();
    }

    @Test
    void validCsrfWithoutAccessToken_postRequest_returnsUnauthorized() throws Exception {
        MvcResult csrf = mockMvc.perform(get("/api/v1/auth/csrf")).andReturn();
        Cookie csrfCookie = csrf.getResponse().getCookie(CSRF_COOKIE);
        String csrfToken = JsonPath.read(csrf.getResponse().getContentAsString(), "$.data.token");

        mockMvc.perform(post("/api/v1/ai/conversations/1/messages")
                        .cookie(csrfCookie)
                        .header(CSRF_HEADER, csrfToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accessTokenWithoutCsrf_postRequest_returnsCsrfRefreshRequired() throws Exception {
        mockMvc.perform(post("/api/v1/ai/conversations/1/messages").cookie(accessCookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data.csrfTokenRefreshRequired").value(true));
    }

    @Test
    void accessTokenWithInvalidCsrf_postRequest_returnsCsrfRefreshRequired() throws Exception {
        Cookie csrfCookie = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andReturn().getResponse().getCookie(CSRF_COOKIE);

        mockMvc.perform(post("/api/v1/ai/conversations/1/messages")
                        .cookie(accessCookie(), csrfCookie)
                        .header(CSRF_HEADER, "invalid-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data.csrfTokenRefreshRequired").value(true));
    }

    @Test
    void accessTokenWithValidCsrf_postRequest_reachesController() throws Exception {
        MvcResult csrf = mockMvc.perform(get("/api/v1/auth/csrf")).andReturn();
        Cookie csrfCookie = csrf.getResponse().getCookie(CSRF_COOKIE);
        String csrfToken = JsonPath.read(csrf.getResponse().getContentAsString(), "$.data.token");

        mockMvc.perform(post("/api/v1/ai/conversations/999999/messages")
                        .cookie(accessCookie(), csrfCookie)
                        .header(CSRF_HEADER, csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientMessageId\":\"00000000-0000-0000-0000-000000000001\",\"content\":\"hi\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void noAuthentication_findHikariConnectionMetrics_returnsOk() throws Exception {
        mockMvc.perform(get("/actuator/metrics/hikaricp.connections.active"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/metrics/hikaricp.connections.pending"))
                .andExpect(status().isOk());
    }

    @Test
    void noAuthentication_findPrometheusMetrics_returnsOk() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(containsString("jvm_")));
    }

    @Test
    void noAuthentication_findOtherActuatorEndpoint_isNotExposed() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
    }
}
