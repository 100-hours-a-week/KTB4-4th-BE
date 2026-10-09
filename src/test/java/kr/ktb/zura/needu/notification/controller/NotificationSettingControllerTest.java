package kr.ktb.zura.needu.notification.controller;

import java.util.List;
import kr.ktb.zura.needu.notification.dto.request.UpdateNotificationSettingRequest;
import kr.ktb.zura.needu.notification.dto.response.NotificationSettingResponse;
import kr.ktb.zura.needu.notification.dto.response.NotificationSettingsResponse;
import kr.ktb.zura.needu.notification.service.NotificationSettingService;
import kr.ktb.zura.needu.notification.type.NotificationSettingType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    void settingsExist_findAllNotificationSettings_returnsDynamicList() throws Exception {
        given(notificationSettingService.findAllNotificationSettings(LOGIN_USER_ID))
                .willReturn(new NotificationSettingsResponse(List.of(
                        new NotificationSettingResponse(NotificationSettingType.FRIEND_JOINED, true, null),
                        new NotificationSettingResponse(NotificationSettingType.MARKETING, false, null)
                )));

        mockMvc.perform(get(URL).with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.settings[0].type").value("FRIEND_JOINED"))
                .andExpect(jsonPath("$.data.settings[0].enabled").value(true))
                .andExpect(jsonPath("$.data.settings[1].type").value("MARKETING"));
    }

    @Test
    void settingTypeGiven_updateNotificationSetting_updatesOnlyThatType() throws Exception {
        UpdateNotificationSettingRequest request = new UpdateNotificationSettingRequest(false);
        given(notificationSettingService.updateNotificationSetting(
                LOGIN_USER_ID, NotificationSettingType.FRIEND_JOINED, request))
                .willReturn(new NotificationSettingResponse(
                        NotificationSettingType.FRIEND_JOINED, false, null));

        mockMvc.perform(patch(URL + "/FRIEND_JOINED").with(authenticatedUser()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("알림 설정을 저장했습니다."))
                .andExpect(jsonPath("$.data.type").value("FRIEND_JOINED"))
                .andExpect(jsonPath("$.data.enabled").value(false));
    }

    @Test
    void enabledMissing_updateNotificationSetting_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(patch(URL + "/FRIEND_JOINED").with(authenticatedUser()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableContent());

        verify(notificationSettingService, never()).updateNotificationSetting(anyLong(), any(), any());
    }

    private static RequestPostProcessor authenticatedUser() {
        return authentication(new UsernamePasswordAuthenticationToken(LOGIN_USER_ID, null, List.of()));
    }
}
