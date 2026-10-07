package kr.ktb.zura.needu.notification.controller;

import java.util.List;
import kr.ktb.zura.needu.notification.dto.request.UpdateNotificationSettingRequest;
import kr.ktb.zura.needu.notification.dto.response.NotificationSettingResponse;
import kr.ktb.zura.needu.notification.service.NotificationSettingService;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationSettingController.class)
class NotificationSettingControllerTest {

    private static final Long LOGIN_USER_ID = 1L;
    private static final String URL = "/api/v1/users/me/settings/notifications";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationSettingService notificationSettingService;

    @Test
    void oneFieldGiven_updateNotificationSetting_passesOnlyThatField() throws Exception {
        UpdateNotificationSettingRequest request = new UpdateNotificationSettingRequest(false, null, null, null);
        given(notificationSettingService.updateNotificationSetting(LOGIN_USER_ID, request))
                .willReturn(new NotificationSettingResponse(false, true, true, false, null));

        mockMvc.perform(patch(URL).with(authenticatedUser()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"friendJoined\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("알림 설정을 저장했습니다."))
                .andExpect(jsonPath("$.data.friendJoined").value(false))
                .andExpect(jsonPath("$.data.friendBirthday").value(true));
    }

    @Test
    void noFieldGiven_updateNotificationSetting_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(patch(URL).with(authenticatedUser()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableContent());

        verify(notificationSettingService, never()).updateNotificationSetting(anyLong(), any());
    }

    private static RequestPostProcessor authenticatedUser() {
        return authentication(new UsernamePasswordAuthenticationToken(LOGIN_USER_ID, null, List.of()));
    }
}
