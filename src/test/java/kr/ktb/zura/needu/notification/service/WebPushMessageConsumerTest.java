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
import kr.ktb.zura.needu.notification.client.WebPushClient;
import kr.ktb.zura.needu.notification.client.WebPushResult;
import kr.ktb.zura.needu.notification.entity.PushSubscription;
import kr.ktb.zura.needu.notification.repository.PushSubscriptionRepository;
import kr.ktb.zura.needu.notification.type.NotificationCategory;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationType;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import tools.jackson.databind.json.JsonMapper;

class WebPushMessageConsumerTest {

    private static final Long FIRST_USER_ID = 1L;
    private static final Long SECOND_USER_ID = 2L;
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 15, 12, 0);

    private final PushSubscriptionRepository pushSubscriptionRepository = mock(PushSubscriptionRepository.class);
    private final WebPushClient webPushClient = mock(WebPushClient.class);
    private final WebPushMessageConsumer consumer =
            new WebPushMessageConsumer(pushSubscriptionRepository, webPushClient);

    @Test
    void declaresWebPushTopicWithMessageType() {
        assertThat(consumer.topic()).isEqualTo("webpush");
        assertThat(consumer.payloadType()).isEqualTo(WebPushMessage.class);
    }

    @Test
    void consume_sendsToEachReceiversSubscriptionsAndDeletesExpiredSubscription() {
        PushSubscription firstSubscription = subscription(11L, FIRST_USER_ID);
        PushSubscription secondSubscription = subscription(12L, FIRST_USER_ID);
        PushSubscription expiredSubscription = subscription(21L, SECOND_USER_ID);
        given(pushSubscriptionRepository.findAllByUserIdIn(List.of(FIRST_USER_ID, SECOND_USER_ID)))
                .willReturn(List.of(firstSubscription, secondSubscription, expiredSubscription));
        given(webPushClient.send(firstSubscription, notification(101L, FIRST_USER_ID)))
                .willReturn(WebPushResult.SUCCESS);
        given(webPushClient.send(secondSubscription, notification(101L, FIRST_USER_ID)))
                .willReturn(WebPushResult.FAILED);
        given(webPushClient.send(expiredSubscription, notification(102L, SECOND_USER_ID)))
                .willReturn(WebPushResult.EXPIRED);

        consumer.consume(message(
                new WebPushMessage.Recipient(101L, FIRST_USER_ID),
                new WebPushMessage.Recipient(102L, SECOND_USER_ID)));

        verify(webPushClient).send(firstSubscription, notification(101L, FIRST_USER_ID));
        verify(webPushClient).send(secondSubscription, notification(101L, FIRST_USER_ID));
        verify(webPushClient).send(expiredSubscription, notification(102L, SECOND_USER_ID));
        verify(pushSubscriptionRepository).deleteById(21L);
        verify(pushSubscriptionRepository, never()).deleteById(11L);
        verify(pushSubscriptionRepository, never()).deleteById(12L);
    }

    @Test
    void consume_continuesWhenOneSubscriptionFails() {
        PushSubscription failingSubscription = subscription(11L, FIRST_USER_ID);
        PushSubscription nextSubscription = subscription(21L, SECOND_USER_ID);
        given(pushSubscriptionRepository.findAllByUserIdIn(List.of(FIRST_USER_ID, SECOND_USER_ID)))
                .willReturn(List.of(failingSubscription, nextSubscription));
        given(webPushClient.send(failingSubscription, notification(101L, FIRST_USER_ID)))
                .willThrow(new IllegalStateException("push server down"));
        given(webPushClient.send(nextSubscription, notification(102L, SECOND_USER_ID)))
                .willReturn(WebPushResult.SUCCESS);

        assertThatCode(() -> consumer.consume(message(
                new WebPushMessage.Recipient(101L, FIRST_USER_ID),
                new WebPushMessage.Recipient(102L, SECOND_USER_ID))))
                .doesNotThrowAnyException();
        verify(webPushClient).send(nextSubscription, notification(102L, SECOND_USER_ID));
    }

    @Test
    void consume_doesNotThrowWhenExpiredSubscriptionDeleteFails() {
        PushSubscription expiredSubscription = subscription(11L, FIRST_USER_ID);
        given(pushSubscriptionRepository.findAllByUserIdIn(List.of(FIRST_USER_ID)))
                .willReturn(List.of(expiredSubscription));
        given(webPushClient.send(expiredSubscription, notification(101L, FIRST_USER_ID)))
                .willReturn(WebPushResult.EXPIRED);
        doThrow(new DataAccessResourceFailureException("db down"))
                .when(pushSubscriptionRepository).deleteById(11L);

        assertThatCode(() -> consumer.consume(message(new WebPushMessage.Recipient(101L, FIRST_USER_ID))))
                .doesNotThrowAnyException();
    }

    @Test
    void consume_throwsWhenSubscriptionLookupFails() {
        given(pushSubscriptionRepository.findAllByUserIdIn(List.of(FIRST_USER_ID)))
                .willThrow(new DataAccessResourceFailureException("db down"));

        assertThatThrownBy(() -> consumer.consume(message(new WebPushMessage.Recipient(101L, FIRST_USER_ID))))
                .isInstanceOf(DataAccessResourceFailureException.class);
        verifyNoInteractions(webPushClient);
    }

    @Test
    void consume_skipsMessageWithoutRecipients() {
        consumer.consume(message());

        verifyNoInteractions(pushSubscriptionRepository, webPushClient);
    }

    @Test
    void consume_sendsNothingToReceiverWithoutSubscription() {
        given(pushSubscriptionRepository.findAllByUserIdIn(List.of(FIRST_USER_ID))).willReturn(List.of());

        consumer.consume(message(new WebPushMessage.Recipient(101L, FIRST_USER_ID)));

        verify(webPushClient, never()).send(any(), any());
    }

    @Test
    void message_keepsContentThroughJsonRoundTrip() {
        JsonMapper jsonMapper = new JsonMapper();
        WebPushMessage message = message(
                new WebPushMessage.Recipient(101L, FIRST_USER_ID),
                new WebPushMessage.Recipient(102L, SECOND_USER_ID));

        WebPushMessage restored = jsonMapper.readValue(jsonMapper.writeValueAsString(message), WebPushMessage.class);

        assertThat(restored).isEqualTo(message);
        assertThat(restored.toNotifications())
                .containsExactly(notification(101L, FIRST_USER_ID), notification(102L, SECOND_USER_ID));
    }

    private WebPushMessage message(WebPushMessage.Recipient... recipients) {
        return new WebPushMessage(
                NotificationCategory.POKE,
                NotificationType.POKE,
                "친구가 콕 찔렀어요.",
                "친구의 알림을 확인해 보세요.",
                NotificationResourceType.USER,
                FIRST_USER_ID,
                CREATED_AT,
                List.of(recipients));
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
                FIRST_USER_ID,
                CREATED_AT);
    }

    private PushSubscription subscription(Long subscriptionId, Long userId) {
        PushSubscription subscription = mock(PushSubscription.class);
        given(subscription.getId()).willReturn(subscriptionId);
        given(subscription.getUserId()).willReturn(userId);
        return subscription;
    }
}
