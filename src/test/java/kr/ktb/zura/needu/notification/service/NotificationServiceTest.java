package kr.ktb.zura.needu.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.notification.dto.request.NotificationSearchCondition;
import kr.ktb.zura.needu.notification.dto.response.NotificationListResponse;
import kr.ktb.zura.needu.notification.dto.response.NotificationReadResponse;
import kr.ktb.zura.needu.notification.dto.response.NotificationSummaryResponse;
import kr.ktb.zura.needu.notification.entity.Notification;
import kr.ktb.zura.needu.notification.entity.NotificationEvent;
import kr.ktb.zura.needu.notification.repository.NotificationRepository;
import kr.ktb.zura.needu.notification.exception.NotificationErrorCode;
import kr.ktb.zura.needu.notification.type.NotificationCategory;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationType;
import kr.ktb.zura.needu.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;

class NotificationServiceTest {

    private static final Long USER_ID = 1L;

    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final UserService userService = mock(UserService.class);
    private final NotificationService notificationService = new NotificationService(notificationRepository, userService);

    @Test
    void unreadNotificationsExist_findNotificationSummary_returnsCountAndTrue() {
        given(notificationRepository.countUnreadByReceiverUserId(USER_ID)).willReturn(4L);

        NotificationSummaryResponse response = notificationService.findNotificationSummary(USER_ID);

        assertThat(response).isEqualTo(new NotificationSummaryResponse(true, 4));
        verify(userService).validateActiveUser(USER_ID);
        verify(notificationRepository).countUnreadByReceiverUserId(USER_ID);
    }

    @Test
    void unreadNotificationsMissing_findNotificationSummary_returnsZeroAndFalse() {
        given(notificationRepository.countUnreadByReceiverUserId(USER_ID)).willReturn(0L);

        NotificationSummaryResponse response = notificationService.findNotificationSummary(USER_ID);

        assertThat(response).isEqualTo(new NotificationSummaryResponse(false, 0));
    }

    @Test
    void filteredNotificationsExist_findAllNotifications_returnsCursorPageAndCounts() {
        LocalDateTime since = LocalDateTime.of(2026, 9, 5, 14, 30);
        Notification first = createNotification(502L, NotificationType.FRIEND_BIRTHDAY);
        Notification second = createNotification(501L, NotificationType.FRIEND_BIRTHDAY);
        given(notificationRepository.findAllVisibleByReceiverUserId(
                USER_ID, List.of(NotificationType.FRIEND_BIRTHDAY), since, null, Limit.of(2)))
                .willReturn(List.of(first, second));
        given(notificationRepository.countUnreadByReceiverUserId(USER_ID)).willReturn(4L);
        given(notificationRepository.countNewByReceiverUserId(
                USER_ID, List.of(NotificationType.FRIEND_BIRTHDAY), since)).willReturn(2L);

        NotificationListResponse response = notificationService.findAllNotifications(
                USER_ID, new NotificationSearchCondition(NotificationCategory.EVENT, null, 1, since));

        assertThat(response.page().items()).hasSize(1);
        assertThat(response.page().items().getFirst().notificationId()).isEqualTo(502L);
        assertThat(NotificationCursor.decode(response.page().nextCursor()).id()).isEqualTo(502L);
        assertThat(response.page().hasNext()).isTrue();
        assertThat(response.unreadCount()).isEqualTo(4);
        assertThat(response.newCount()).isEqualTo(2);
        assertThat(response.serverTime()).isNotNull();
        verify(userService).validateActiveUser(USER_ID);
    }

    @Test
    void ownedUnreadNotification_readNotification_marksReadAndReturnsRemainingCount() {
        LocalDateTime readAt = LocalDateTime.of(2026, 9, 5, 14, 31);
        Notification notification = createNotification(501L, NotificationType.FRIEND_BIRTHDAY);
        given(notification.getReadAt()).willReturn(readAt);
        given(notification.getReceiverUserId()).willReturn(USER_ID);
        given(notificationRepository.findOneById(501L))
                .willReturn(Optional.of(notification));
        given(notificationRepository.countUnreadByReceiverUserId(USER_ID)).willReturn(3L);

        NotificationReadResponse response = notificationService.readNotification(USER_ID, 501L);

        assertThat(response).isEqualTo(new NotificationReadResponse(
                501L, readAt, NotificationResourceType.USER, 321L, true, 3L));
        verify(userService).validateActiveUser(USER_ID);
        verify(notification).read();
    }

    @Test
    void notificationMissing_readNotification_throwsNotFound() {
        given(notificationRepository.findOneById(501L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.readNotification(USER_ID, 501L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(NotificationErrorCode.NOTIFICATION_NOT_FOUND);

        verify(userService).validateActiveUser(USER_ID);
        verify(notificationRepository, never()).countUnreadByReceiverUserId(USER_ID);
    }

    @Test
    void notificationOwnedByAnotherUser_readNotification_throwsForbidden() {
        Notification notification = createNotification(501L, NotificationType.FRIEND_BIRTHDAY);
        given(notification.getReceiverUserId()).willReturn(2L);
        given(notificationRepository.findOneById(501L)).willReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.readNotification(USER_ID, 501L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(NotificationErrorCode.NOTIFICATION_FORBIDDEN);

        verify(notification, never()).read();
        verify(notificationRepository, never()).countUnreadByReceiverUserId(USER_ID);
    }

    @Test
    void notificationAlreadyDeleted_readNotification_throwsNotFound() {
        Notification notification = createNotification(501L, NotificationType.FRIEND_BIRTHDAY);
        given(notification.getReceiverUserId()).willReturn(USER_ID);
        given(notification.isDeleted()).willReturn(true);
        given(notificationRepository.findOneById(501L)).willReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.readNotification(USER_ID, 501L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(NotificationErrorCode.NOTIFICATION_NOT_FOUND);

        verify(notification, never()).read();
        verify(notificationRepository, never()).countUnreadByReceiverUserId(USER_ID);
    }

    @Test
    void ownedNotification_deleteNotification_marksNotificationDeleted() {
        Notification notification = createNotification(501L, NotificationType.FRIEND_BIRTHDAY);
        given(notification.getReceiverUserId()).willReturn(USER_ID);
        given(notificationRepository.findOneById(501L)).willReturn(Optional.of(notification));

        notificationService.deleteNotification(USER_ID, 501L);

        verify(userService).validateActiveUser(USER_ID);
        verify(notification).delete();
    }

    @Test
    void notificationMissing_deleteNotification_throwsNotFound() {
        given(notificationRepository.findOneById(501L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.deleteNotification(USER_ID, 501L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(NotificationErrorCode.NOTIFICATION_NOT_FOUND);
    }

    @Test
    void notificationOwnedByAnotherUser_deleteNotification_throwsForbidden() {
        Notification notification = createNotification(501L, NotificationType.FRIEND_BIRTHDAY);
        given(notification.getReceiverUserId()).willReturn(2L);
        given(notificationRepository.findOneById(501L)).willReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.deleteNotification(USER_ID, 501L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(NotificationErrorCode.NOTIFICATION_FORBIDDEN);

        verify(notification, never()).delete();
    }

    @Test
    void notificationAlreadyDeleted_deleteNotification_throwsNotFound() {
        Notification notification = createNotification(501L, NotificationType.FRIEND_BIRTHDAY);
        given(notification.getReceiverUserId()).willReturn(USER_ID);
        given(notification.isDeleted()).willReturn(true);
        given(notificationRepository.findOneById(501L)).willReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.deleteNotification(USER_ID, 501L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(NotificationErrorCode.NOTIFICATION_NOT_FOUND);

        verify(notification, never()).delete();
    }

    private Notification createNotification(Long id, NotificationType type) {
        NotificationEvent event = mock(NotificationEvent.class);
        given(event.getCategory()).willReturn(type.getCategory());
        given(event.getType()).willReturn(type);
        given(event.getTitle()).willReturn("알림 제목");
        given(event.getBody()).willReturn("알림 내용");
        given(event.getResourceType()).willReturn(NotificationResourceType.USER);
        given(event.getResourceId()).willReturn(321L);

        Notification notification = mock(Notification.class);
        given(notification.getId()).willReturn(id);
        given(notification.getEvent()).willReturn(event);
        given(notification.getCreatedAt()).willReturn(LocalDateTime.of(2026, 9, 5, 10, 0));
        return notification;
    }
}
