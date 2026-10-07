package kr.ktb.zura.needu.notification.controller;

import java.time.LocalDateTime;
import java.util.List;
import kr.ktb.zura.needu.notification.dto.request.NotificationSearchCondition;
import kr.ktb.zura.needu.notification.dto.response.NotificationListResponse;
import kr.ktb.zura.needu.notification.dto.response.NotificationResponse;
import kr.ktb.zura.needu.notification.service.NotificationService;
import kr.ktb.zura.needu.notification.service.NotificationStreamService;
import kr.ktb.zura.needu.notification.type.NotificationCategory;
import kr.ktb.zura.needu.notification.type.NotificationTargetType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    private static final Long LOGIN_USER_ID = 1L;
    private static final String LIST_URL = "/api/v1/notifications";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private NotificationStreamService notificationStreamService;

    @Test
    void parametersOmitted_findAllNotifications_usesDefaultsAndPutsCountsInData() throws Exception {
        LocalDateTime serverTime = LocalDateTime.of(2026, 9, 5, 14, 30);
        NotificationResponse notification = new NotificationResponse(501L, NotificationCategory.EVENT,
                "수연 님의 생일이 8일 남았어요.", "지금 선물을 준비하면 여유 있게 도착해요.",
                NotificationTargetType.FRIEND_DETAIL, "321", true, null, serverTime.minusDays(1));
        given(notificationService.findAllNotifications(LOGIN_USER_ID, new NotificationSearchCondition(null, null, 20, null)))
                .willReturn(new NotificationListResponse(List.of(notification), 4, 0, serverTime, "next", true));

        mockMvc.perform(get(LIST_URL).with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("알림을 조회했습니다."))
                .andExpect(jsonPath("$.data.items[0].category").value("EVENT"))
                .andExpect(jsonPath("$.data.items[0].targetType").value("FRIEND_DETAIL"))
                .andExpect(jsonPath("$.data.items[0].targetId").value("321"))
                .andExpect(jsonPath("$.data.unreadCount").value(4))
                .andExpect(jsonPath("$.data.newCount").value(0))
                .andExpect(jsonPath("$.data.serverTime").value("2026-09-05T14:30:00"))
                .andExpect(jsonPath("$.nextCursor").value("next"))
                .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    void categoryAndSinceGiven_findAllNotifications_passesConditionToService() throws Exception {
        LocalDateTime since = LocalDateTime.of(2026, 9, 5, 14, 30);
        NotificationSearchCondition condition = new NotificationSearchCondition(NotificationCategory.POKE, null, 10, since);
        given(notificationService.findAllNotifications(LOGIN_USER_ID, condition))
                .willReturn(new NotificationListResponse(List.of(), 0, 0, since, null, false));

        mockMvc.perform(get(LIST_URL)
                        .param("category", "POKE")
                        .param("size", "10")
                        .param("since", "2026-09-05T14:30:00")
                        .with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());

        verify(notificationService).findAllNotifications(LOGIN_USER_ID, condition);
    }

    @Test
    void unknownCategory_findAllNotifications_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(get(LIST_URL).param("category", "UNKNOWN").with(authenticatedUser()))
                .andExpect(status().isUnprocessableContent());

        verify(notificationService, never()).findAllNotifications(anyLong(), any());
    }

    @Test
    void malformedSince_findAllNotifications_returnsBadRequest() throws Exception {
        mockMvc.perform(get(LIST_URL).param("since", "2026-09-05").with(authenticatedUser()))
                .andExpect(status().isBadRequest());

        verify(notificationService, never()).findAllNotifications(anyLong(), any());
    }

    @Test
    void myNotification_deleteNotification_returnsNoContent() throws Exception {
        mockMvc.perform(delete(LIST_URL + "/{notificationId}", 501L).with(authenticatedUser()).with(csrf()))
                .andExpect(status().isNoContent());

        verify(notificationService).deleteNotification(LOGIN_USER_ID, 501L);
    }

    private static RequestPostProcessor authenticatedUser() {
        return authentication(new UsernamePasswordAuthenticationToken(LOGIN_USER_ID, null, List.of()));
    }
}
