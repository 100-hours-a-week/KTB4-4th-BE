package kr.ktb.zura.needu.notification.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import kr.ktb.zura.needu.notification.type.NotificationCategory;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationType;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class WebPushNotificationCreatedEventListenerTest {

    private final WebPushDeliveryService webPushDeliveryService = mock(WebPushDeliveryService.class);
    private final WebPushNotificationCreatedEventListener listener =
            new WebPushNotificationCreatedEventListener(webPushDeliveryService);

    @Test
    void notificationsCreated_sendWebPush_deliversEventNotifications() {
        List<NotificationsCreatedEvent.CreatedNotification> notifications =
                List.of(notification(101L, 1L), notification(102L, 2L));

        listener.sendWebPush(new NotificationsCreatedEvent(notifications));

        verify(webPushDeliveryService).deliver(notifications);
    }

    @Test
    void deliveryThrows_sendWebPush_doesNotThrow() {
        List<NotificationsCreatedEvent.CreatedNotification> notifications = List.of(notification(101L, 1L));
        doThrow(new DataAccessResourceFailureException("db down")).when(webPushDeliveryService).deliver(notifications);

        assertThatCode(() -> listener.sendWebPush(new NotificationsCreatedEvent(notifications)))
                .doesNotThrowAnyException();
    }

    private NotificationsCreatedEvent.CreatedNotification notification(Long notificationId, Long userId) {
        return new NotificationsCreatedEvent.CreatedNotification(
                notificationId,
                userId,
                NotificationCategory.POKE,
                NotificationType.POKE,
                "친구가 콕 찔렀어요.",
                "친구의 알림을 확인해 보세요.",
                NotificationResourceType.USER,
                userId,
                LocalDateTime.of(2026, 10, 15, 12, 0)
        );
    }
}
