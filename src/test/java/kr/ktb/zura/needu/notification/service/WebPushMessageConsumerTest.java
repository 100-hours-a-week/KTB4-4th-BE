package kr.ktb.zura.needu.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import tools.jackson.databind.json.JsonMapper;

class WebPushMessageConsumerTest {

    private static final Long FIRST_USER_ID = 1L;
    private static final Long SECOND_USER_ID = 2L;
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 15, 12, 0);

    private final WebPushDeliveryService webPushDeliveryService = mock(WebPushDeliveryService.class);
    private final WebPushMessageConsumer consumer = new WebPushMessageConsumer(webPushDeliveryService);

    @Test
    void declaresWebPushTopicWithMessageType() {
        assertThat(consumer.topic()).isEqualTo("webpush");
        assertThat(consumer.payloadType()).isEqualTo(WebPushMessage.class);
    }

    @Test
    void message_consume_deliversNotificationsOfEachRecipient() {
        consumer.consume(message(
                new WebPushMessage.Recipient(101L, FIRST_USER_ID),
                new WebPushMessage.Recipient(102L, SECOND_USER_ID)));

        verify(webPushDeliveryService).deliver(List.of(
                notification(101L, FIRST_USER_ID), notification(102L, SECOND_USER_ID)));
    }

    @Test
    void deliveryThrows_consume_propagatesToLeaveMessagePending() {
        List<NotificationsCreatedEvent.CreatedNotification> notifications =
                List.of(notification(101L, FIRST_USER_ID));
        doThrow(new DataAccessResourceFailureException("db down")).when(webPushDeliveryService).deliver(notifications);

        assertThatThrownBy(() -> consumer.consume(message(new WebPushMessage.Recipient(101L, FIRST_USER_ID))))
                .isInstanceOf(DataAccessResourceFailureException.class);
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
}
