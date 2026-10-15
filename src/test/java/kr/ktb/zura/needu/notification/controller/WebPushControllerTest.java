package kr.ktb.zura.needu.notification.controller;

import kr.ktb.zura.needu.common.exception.GlobalExceptionHandler;
import kr.ktb.zura.needu.notification.service.VapidPublicKeyService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebPushControllerTest {

    private static final String URL = "/api/v1/notifications/web-push/public-key";

    @Test
    void publicKeyConfigured_findVapidPublicKey_returnsConfiguredKey() throws Exception {
        MockMvc mockMvc = mockMvc("test-public-key");

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("VAPID 공개키를 조회했습니다."))
                .andExpect(jsonPath("$.data.publicKey").value("test-public-key"));
    }

    @Test
    void publicKeyMissing_findVapidPublicKey_returnsServiceUnavailable() throws Exception {
        MockMvc mockMvc = mockMvc("");

        mockMvc.perform(get(URL))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("VAPID 공개키를 사용할 수 없습니다."));
    }

    private MockMvc mockMvc(String publicKey) {
        return MockMvcBuilders.standaloneSetup(
                        new WebPushController(new VapidPublicKeyService(publicKey)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }
}
