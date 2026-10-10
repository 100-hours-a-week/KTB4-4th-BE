package kr.ktb.zura.needu.notification.controller;

import java.time.LocalDateTime;
import java.util.List;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.response.CursorPageResponse;
import kr.ktb.zura.needu.notification.dto.request.NotificationSearchCondition;
import kr.ktb.zura.needu.notification.dto.response.NotificationListResponse;
import kr.ktb.zura.needu.notification.dto.response.NotificationReadResponse;
import kr.ktb.zura.needu.notification.dto.response.NotificationResponse;
import kr.ktb.zura.needu.notification.dto.response.NotificationSummaryResponse;
import kr.ktb.zura.needu.notification.exception.NotificationErrorCode;
import kr.ktb.zura.needu.notification.service.NotificationService;
import kr.ktb.zura.needu.notification.service.NotificationStreamService;
import kr.ktb.zura.needu.notification.type.NotificationCategory;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationType;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    private static final Long LOGIN_USER_ID = 1L;
    private static final String LIST_URL = "/api/v1/notifications";
    private static final String SUMMARY_URL = LIST_URL + "/summary";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private NotificationStreamService notificationStreamService;

    @Test
    void authenticatedUser_findNotificationSummary_returnsUnreadStatus() throws Exception {
        given(notificationService.findNotificationSummary(LOGIN_USER_ID))
                .willReturn(new NotificationSummaryResponse(true, 4));

        mockMvc.perform(get(SUMMARY_URL).with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("미읽음 알림 상태를 조회했습니다."))
                .andExpect(jsonPath("$.data.hasUnread").value(true))
                .andExpect(jsonPath("$.data.unreadCount").value(4));

        verify(notificationService).findNotificationSummary(LOGIN_USER_ID);
    }

    @Test
    void unauthenticatedUser_findNotificationSummary_returnsUnauthorized() throws Exception {
        mockMvc.perform(get(SUMMARY_URL))
                .andExpect(status().isUnauthorized());

        verify(notificationService, never()).findNotificationSummary(anyLong());
    }

    @Test
    void parametersOmitted_findAllNotifications_usesDefaultsAndPutsCountsInData() throws Exception {
        LocalDateTime serverTime = LocalDateTime.of(2026, 9, 5, 14, 30);
        NotificationResponse notification = new NotificationResponse(501L, NotificationCategory.EVENT,
                NotificationType.FRIEND_BIRTHDAY,
                "수연 님의 생일이 8일 남았어요.", "지금 선물을 준비하면 여유 있게 도착해요.",
                NotificationResourceType.USER, 321L, true, null, serverTime.minusDays(1));
        given(notificationService.findAllNotifications(LOGIN_USER_ID, new NotificationSearchCondition(null, null, 20, null)))
                .willReturn(new NotificationListResponse(
                        new CursorPageResponse<>(List.of(notification), "next", true), 4, 0, serverTime));

        mockMvc.perform(get(LIST_URL).with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("알림을 조회했습니다."))
                .andExpect(jsonPath("$.data.items[0].category").value("EVENT"))
                .andExpect(jsonPath("$.data.items[0].type").value("FRIEND_BIRTHDAY"))
                .andExpect(jsonPath("$.data.items[0].resourceType").value("USER"))
                .andExpect(jsonPath("$.data.items[0].resourceId").value(321))
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
                .willReturn(new NotificationListResponse(
                        new CursorPageResponse<>(List.of(), null, false), 0, 0, since));

        mockMvc.perform(get(LIST_URL)
                        .param("category", "POKE")
                        .param("cursor", "")
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
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message")
                        .value("입력 값이 유효하지 않습니다. 입력한 내용을 확인해 주세요."));

        verify(notificationService, never()).findAllNotifications(anyLong(), any());
    }

    @Test
    void sizeOutOfRange_findAllNotifications_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(get(LIST_URL).param("size", "51").with(authenticatedUser()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message")
                        .value("입력 값이 유효하지 않습니다. 입력한 내용을 확인해 주세요."));

        verify(notificationService, never()).findAllNotifications(anyLong(), any());
    }

    @Test
    void malformedSince_findAllNotifications_returnsBadRequest() throws Exception {
        mockMvc.perform(get(LIST_URL).param("since", "2026-09-05").with(authenticatedUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청 형식이 올바르지 않습니다."));

        verify(notificationService, never()).findAllNotifications(anyLong(), any());
    }

    @Test
    void unexpectedError_findAllNotifications_returnsInternalServerError() throws Exception {
        given(notificationService.findAllNotifications(
                LOGIN_USER_ID, new NotificationSearchCondition(null, null, 20, null)))
                .willThrow(new IllegalStateException("unexpected"));

        mockMvc.perform(get(LIST_URL).with(authenticatedUser()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("요청을 처리하지 못했습니다."));
    }

    @Test
    void ownedNotification_readNotification_returnsReadResult() throws Exception {
        LocalDateTime readAt = LocalDateTime.of(2026, 9, 5, 14, 31);
        given(notificationService.readNotification(LOGIN_USER_ID, 501L))
                .willReturn(new NotificationReadResponse(
                        501L, readAt, NotificationResourceType.USER, 321L, true, 3L));

        mockMvc.perform(patch(LIST_URL + "/{notificationId}", 501L)
                        .with(authenticatedUser()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("알림을 읽음 처리했습니다."))
                .andExpect(jsonPath("$.data.notificationId").value(501))
                .andExpect(jsonPath("$.data.readAt").value("2026-09-05T14:31:00"))
                .andExpect(jsonPath("$.data.resourceType").value("USER"))
                .andExpect(jsonPath("$.data.resourceId").value(321))
                .andExpect(jsonPath("$.data.targetAvailable").value(true))
                .andExpect(jsonPath("$.data.unreadCount").value(3))
                .andExpect(jsonPath("$.data.type").doesNotExist());
    }

    @Test
    void csrfMissing_readNotification_returnsCsrfForbidden() throws Exception {
        mockMvc.perform(patch(LIST_URL + "/{notificationId}", 501L).with(authenticatedUser()))
                .andExpect(status().isForbidden());

        verify(notificationService, never()).readNotification(anyLong(), anyLong());
    }

    @Test
    void notificationNotOwnedByUser_readNotification_returnsForbidden() throws Exception {
        given(notificationService.readNotification(LOGIN_USER_ID, 501L))
                .willThrow(new BusinessException(NotificationErrorCode.NOTIFICATION_FORBIDDEN));

        mockMvc.perform(patch(LIST_URL + "/{notificationId}", 501L)
                        .with(authenticatedUser()).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("접근 할 수 없는 알림입니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void notificationMissing_readNotification_returnsNotFound() throws Exception {
        given(notificationService.readNotification(LOGIN_USER_ID, 501L))
                .willThrow(new BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));

        mockMvc.perform(patch(LIST_URL + "/{notificationId}", 501L)
                        .with(authenticatedUser()).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("알림을 찾을 수 없습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void unexpectedError_readNotification_returnsInternalServerError() throws Exception {
        given(notificationService.readNotification(LOGIN_USER_ID, 501L))
                .willThrow(new IllegalStateException("unexpected"));

        mockMvc.perform(patch(LIST_URL + "/{notificationId}", 501L)
                        .with(authenticatedUser()).with(csrf()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("요청을 처리하지 못했습니다."));
    }

    @Test
    void myNotification_deleteNotification_returnsNoContent() throws Exception {
        mockMvc.perform(delete(LIST_URL + "/{notificationId}", 501L).with(authenticatedUser()).with(csrf()))
                .andExpect(status().isNoContent());

        verify(notificationService).deleteNotification(LOGIN_USER_ID, 501L);
    }

    @Test
    void csrfMissing_deleteNotification_returnsForbidden() throws Exception {
        mockMvc.perform(delete(LIST_URL + "/{notificationId}", 501L).with(authenticatedUser()))
                .andExpect(status().isForbidden());

        verify(notificationService, never()).deleteNotification(anyLong(), anyLong());
    }

    @Test
    void notificationMissing_deleteNotification_returnsNotFound() throws Exception {
        doThrow(new BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND))
                .when(notificationService).deleteNotification(LOGIN_USER_ID, 501L);

        mockMvc.perform(delete(LIST_URL + "/{notificationId}", 501L)
                        .with(authenticatedUser()).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("알림을 찾을 수 없습니다."));
    }

    @Test
    void notificationNotOwnedByUser_deleteNotification_returnsForbidden() throws Exception {
        doThrow(new BusinessException(NotificationErrorCode.NOTIFICATION_FORBIDDEN))
                .when(notificationService).deleteNotification(LOGIN_USER_ID, 501L);

        mockMvc.perform(delete(LIST_URL + "/{notificationId}", 501L)
                        .with(authenticatedUser()).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("접근 할 수 없는 알림입니다."));
    }

    @Test
    void unexpectedError_deleteNotification_returnsInternalServerError() throws Exception {
        doThrow(new IllegalStateException("unexpected"))
                .when(notificationService).deleteNotification(LOGIN_USER_ID, 501L);

        mockMvc.perform(delete(LIST_URL + "/{notificationId}", 501L)
                        .with(authenticatedUser()).with(csrf()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("요청을 처리하지 못했습니다."));
    }

    private static RequestPostProcessor authenticatedUser() {
        return authentication(new UsernamePasswordAuthenticationToken(LOGIN_USER_ID, null, List.of()));
    }
}
