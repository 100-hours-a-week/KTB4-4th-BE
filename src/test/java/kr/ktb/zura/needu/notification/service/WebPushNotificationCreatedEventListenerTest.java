package kr.ktb.zura.needu.notification.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import kr.ktb.zura.needu.notification.client.WebPushClient;
import kr.ktb.zura.needu.notification.client.WebPushResult;
import kr.ktb.zura.needu.notification.entity.PushSubscription;
import kr.ktb.zura.needu.notification.repository.PushSubscriptionRepository;
import kr.ktb.zura.needu.notification.type.NotificationCategory;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationType;
import org.junit.jupiter.api.Test;

class WebPushNotificationCreatedEventListenerTest {

    private static final Long FIRST_USER_ID = 1L;
    private static final Long SECOND_USER_ID = 2L;

    private final PushSubscriptionRepository pushSubscriptionRepository =
            mock(PushSubscriptionRepository.class);
    private final WebPushClient webPushClient = mock(WebPushClient.class);
    private final WebPushNotificationCreatedEventListener listener =
            new WebPushNotificationCreatedEventListener(
                    pushSubscriptionRepository,
                    webPushClient
            );

    @Test
    void notificationsCreated_publishesOnlyToEachReceiversSubscriptionsAndDeletesExpiredSubscription() {
        PushSubscription firstSubscription = subscription(11L, FIRST_USER_ID);
        PushSubscription secondSubscription = subscription(12L, FIRST_USER_ID);
        PushSubscription expiredSubscription = subscription(21L, SECOND_USER_ID);
        NotificationsCreatedEvent.CreatedNotification firstNotification = notification(101L, FIRST_USER_ID);
        NotificationsCreatedEvent.CreatedNotification secondNotification = notification(102L, SECOND_USER_ID);
        given(pushSubscriptionRepository.findAllByUserIdIn(List.of(FIRST_USER_ID, SECOND_USER_ID)))
                .willReturn(List.of(firstSubscription, secondSubscription, expiredSubscription));
        given(webPushClient.send(firstSubscription, firstNotification))
                .willReturn(WebPushResult.SUCCESS);
        given(webPushClient.send(secondSubscription, firstNotification))
                .willReturn(WebPushResult.FAILED);
        given(webPushClient.send(expiredSubscription, secondNotification))
                .willReturn(WebPushResult.EXPIRED);

        listener.sendWebPush(new NotificationsCreatedEvent(List.of(firstNotification, secondNotification)));

        verify(webPushClient).send(firstSubscription, firstNotification);
        verify(webPushClient).send(secondSubscription, firstNotification);
        verify(webPushClient).send(expiredSubscription, secondNotification);
        verify(pushSubscriptionRepository).deleteById(21L);
        verify(pushSubscriptionRepository, never()).deleteById(11L);
        verify(pushSubscriptionRepository, never()).deleteById(12L);
    }

    private PushSubscription subscription(Long subscriptionId, Long userId) {
        PushSubscription subscription = mock(PushSubscription.class);
        given(subscription.getId()).willReturn(subscriptionId);
        given(subscription.getUserId()).willReturn(userId);
        return subscription;
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
