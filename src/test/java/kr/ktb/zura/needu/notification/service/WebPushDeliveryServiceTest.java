package kr.ktb.zura.needu.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import kr.ktb.zura.needu.notification.client.WebPushClient;
import kr.ktb.zura.needu.notification.client.WebPushResult;
import kr.ktb.zura.needu.notification.entity.PushSubscription;
import kr.ktb.zura.needu.notification.repository.PushSubscriptionRepository;
import kr.ktb.zura.needu.notification.type.NotificationCategory;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationType;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class WebPushDeliveryServiceTest {

    private static final Long FIRST_USER_ID = 1L;
    private static final Long SECOND_USER_ID = 2L;

    private final PushSubscriptionRepository pushSubscriptionRepository = mock(PushSubscriptionRepository.class);
    private final WebPushClient webPushClient = mock(WebPushClient.class);
    private final WebPushDeliveryService deliveryService =
            new WebPushDeliveryService(pushSubscriptionRepository, webPushClient);

    @Test
    void notifications_deliver_sendsToEachReceiversSubscriptionsAndDeletesExpiredSubscription() {
        PushSubscription firstSubscription = subscription(11L, FIRST_USER_ID);
        PushSubscription secondSubscription = subscription(12L, FIRST_USER_ID);
        PushSubscription expiredSubscription = subscription(21L, SECOND_USER_ID);
        NotificationsCreatedEvent.CreatedNotification firstNotification = notification(101L, FIRST_USER_ID);
        NotificationsCreatedEvent.CreatedNotification secondNotification = notification(102L, SECOND_USER_ID);
        given(pushSubscriptionRepository.findAllByUserIdIn(List.of(FIRST_USER_ID, SECOND_USER_ID)))
                .willReturn(List.of(firstSubscription, secondSubscription, expiredSubscription));
        given(webPushClient.send(firstSubscription, firstNotification)).willReturn(WebPushResult.SUCCESS);
        given(webPushClient.send(secondSubscription, firstNotification)).willReturn(WebPushResult.FAILED);
        given(webPushClient.send(expiredSubscription, secondNotification)).willReturn(WebPushResult.EXPIRED);

        deliveryService.deliver(List.of(firstNotification, secondNotification));

        verify(webPushClient).send(firstSubscription, firstNotification);
        verify(webPushClient).send(secondSubscription, firstNotification);
        verify(webPushClient).send(expiredSubscription, secondNotification);
        verify(pushSubscriptionRepository).deleteById(21L);
        verify(pushSubscriptionRepository, never()).deleteById(11L);
        verify(pushSubscriptionRepository, never()).deleteById(12L);
    }

    @Test
    void sendExecutor_deliver_sendsEachSubscriptionThroughExecutor() {
        PushSubscription firstSubscription = subscription(11L, FIRST_USER_ID);
        PushSubscription secondSubscription = subscription(12L, FIRST_USER_ID);
        PushSubscription thirdSubscription = subscription(21L, SECOND_USER_ID);
        given(pushSubscriptionRepository.findAllByUserIdIn(List.of(FIRST_USER_ID, SECOND_USER_ID)))
                .willReturn(List.of(firstSubscription, secondSubscription, thirdSubscription));
        given(webPushClient.send(any(), any())).willReturn(WebPushResult.SUCCESS);
        AtomicInteger executed = new AtomicInteger();
        Executor countingExecutor = task -> {
            executed.incrementAndGet();
            task.run();
        };

        deliveryService.deliver(
                List.of(notification(101L, FIRST_USER_ID), notification(102L, SECOND_USER_ID)), countingExecutor);

        assertThat(executed).hasValue(3);
        verify(webPushClient).send(firstSubscription, notification(101L, FIRST_USER_ID));
        verify(webPushClient).send(secondSubscription, notification(101L, FIRST_USER_ID));
        verify(webPushClient).send(thirdSubscription, notification(102L, SECOND_USER_ID));
    }

    @Test
    void sendThrowsForOneSubscription_deliver_continuesWithNextSubscription() {
        PushSubscription failingSubscription = subscription(11L, FIRST_USER_ID);
        PushSubscription nextSubscription = subscription(21L, SECOND_USER_ID);
        given(pushSubscriptionRepository.findAllByUserIdIn(List.of(FIRST_USER_ID, SECOND_USER_ID)))
                .willReturn(List.of(failingSubscription, nextSubscription));
        given(webPushClient.send(failingSubscription, notification(101L, FIRST_USER_ID)))
                .willThrow(new IllegalStateException("push server down"));
        given(webPushClient.send(nextSubscription, notification(102L, SECOND_USER_ID)))
                .willReturn(WebPushResult.SUCCESS);

        assertThatCode(() -> deliveryService.deliver(List.of(
                notification(101L, FIRST_USER_ID), notification(102L, SECOND_USER_ID))))
                .doesNotThrowAnyException();
        verify(webPushClient).send(nextSubscription, notification(102L, SECOND_USER_ID));
    }

    @Test
    void expiredSubscriptionDeleteFails_deliver_doesNotThrow() {
        PushSubscription expiredSubscription = subscription(11L, FIRST_USER_ID);
        given(pushSubscriptionRepository.findAllByUserIdIn(List.of(FIRST_USER_ID)))
                .willReturn(List.of(expiredSubscription));
        given(webPushClient.send(expiredSubscription, notification(101L, FIRST_USER_ID)))
                .willReturn(WebPushResult.EXPIRED);
        doThrow(new DataAccessResourceFailureException("db down"))
                .when(pushSubscriptionRepository).deleteById(11L);

        assertThatCode(() -> deliveryService.deliver(List.of(notification(101L, FIRST_USER_ID))))
                .doesNotThrowAnyException();
    }

    @Test
    void subscriptionLookupFails_deliver_throwsWithoutSending() {
        given(pushSubscriptionRepository.findAllByUserIdIn(List.of(FIRST_USER_ID)))
                .willThrow(new DataAccessResourceFailureException("db down"));

        assertThatThrownBy(() -> deliveryService.deliver(List.of(notification(101L, FIRST_USER_ID))))
                .isInstanceOf(DataAccessResourceFailureException.class);
        verifyNoInteractions(webPushClient);
    }

    @Test
    void noNotifications_deliver_doesNotAccessRepositoryOrClient() {
        deliveryService.deliver(List.of());

        verifyNoInteractions(pushSubscriptionRepository, webPushClient);
    }

    @Test
    void receiverWithoutSubscription_deliver_sendsNothing() {
        given(pushSubscriptionRepository.findAllByUserIdIn(List.of(FIRST_USER_ID))).willReturn(List.of());

        deliveryService.deliver(List.of(notification(101L, FIRST_USER_ID)));

        verify(webPushClient, never()).send(any(), any());
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
