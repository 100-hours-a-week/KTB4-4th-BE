package kr.ktb.zura.needu.common.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "management.endpoints.web.exposure.include=health,metrics")
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class SecurityConfigTest {

    private final MockMvc mockMvc;

    SecurityConfigTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
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
      
    void noAuthentication_findHikariConnectionMetrics_returnsOk() throws Exception {
        mockMvc.perform(get("/actuator/metrics/hikaricp.connections.active"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/metrics/hikaricp.connections.pending"))
                .andExpect(status().isOk());
    }

    @Test
    void noAuthentication_findHikariConnectionMetrics_returnsOk() throws Exception {
        mockMvc.perform(get("/actuator/metrics/hikaricp.connections.active"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/metrics/hikaricp.connections.pending"))
                .andExpect(status().isOk());
    }

    @Test
    void noAuthentication_findOtherActuatorEndpoint_isNotExposed() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
    }
}
