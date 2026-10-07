package kr.ktb.zura.needu.notification.controller;

import java.time.LocalDateTime;
import java.util.List;
import kr.ktb.zura.needu.notification.dto.response.PushSubscriptionResponse;
import kr.ktb.zura.needu.notification.service.PushSubscriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PushSubscriptionController.class)
class PushSubscriptionControllerTest {

    private static final Long LOGIN_USER_ID = 1L;
    private static final String URL = "/api/v1/users/me/push-subscriptions";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PushSubscriptionService pushSubscriptionService;

    @Test
    void validSubscription_subscribe_returnsEnabled() throws Exception {
        given(pushSubscriptionService.subscribe(eq(LOGIN_USER_ID), any()))
                .willReturn(new PushSubscriptionResponse(true, LocalDateTime.of(2026, 10, 7, 10, 30)));

        mockMvc.perform(post(URL).with(authenticatedUser()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(subscriptionJson("https://fcm.googleapis.com/fcm/send/abc")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("웹 푸시 알림을 켰습니다."))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.updatedAt").value("2026-10-07T10:30:00"));
    }

    @Test
    void nonHttpsEndpoint_subscribe_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(post(URL).with(authenticatedUser()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(subscriptionJson("http://fcm.googleapis.com/fcm/send/abc")))
                .andExpect(status().isUnprocessableContent());

        verify(pushSubscriptionService, never()).subscribe(anyLong(), any());
    }

    @Test
    void missingKeys_subscribe_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(post(URL).with(authenticatedUser()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\": \"https://fcm.googleapis.com/fcm/send/abc\"}"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void endpointGiven_unsubscribe_returnsNoContent() throws Exception {
        mockMvc.perform(delete(URL).with(authenticatedUser()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\": \"https://fcm.googleapis.com/fcm/send/abc\"}"))
                .andExpect(status().isNoContent());

        verify(pushSubscriptionService).unsubscribe(eq(LOGIN_USER_ID), any());
    }

    private static String subscriptionJson(String endpoint) {
        return """
                {"endpoint": "%s", "expirationTime": null, "keys": {"p256dh": "BNcRdreALRFX", "auth": "tBHItJI5svbpez7KI4CCXg"}}
                """.formatted(endpoint);
    }

    private static RequestPostProcessor authenticatedUser() {
        return authentication(new UsernamePasswordAuthenticationToken(LOGIN_USER_ID, null, List.of()));
    }
}
