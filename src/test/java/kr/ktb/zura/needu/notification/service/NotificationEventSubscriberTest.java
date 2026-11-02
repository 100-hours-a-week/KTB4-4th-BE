package kr.ktb.zura.needu.notification.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.util.List;
import kr.ktb.zura.needu.notification.sse.NotificationRedisEvent;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;
import tools.jackson.databind.json.JsonMapper;

class NotificationEventSubscriberTest {

    private static final byte[] CHANNEL = "backend:test:notification:events".getBytes(StandardCharsets.UTF_8);

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final NotificationStreamService notificationStreamService = mock(NotificationStreamService.class);
    private final NotificationEventSubscriber subscriber =
            new NotificationEventSubscriber(jsonMapper, notificationStreamService);

    @Test
    void publishedEventMessage_onMessage_sendsToStreamService() {
        NotificationRedisEvent event = new NotificationRedisEvent(
                jsonMapper.createObjectNode().put("type", "FRIEND_BIRTHDAY").put("title", "친구의 생일이 7일 남았어요."),
                List.of(
                        new NotificationRedisEvent.Recipient(
                                1L, jsonMapper.createObjectNode().put("notificationId", 10L), 3L),
                        new NotificationRedisEvent.Recipient(
                                2L, jsonMapper.createObjectNode().put("notificationId", 11L), 1L)
                )
        );

        subscriber.onMessage(toMessage(jsonMapper.writeValueAsString(event)), CHANNEL);

        verify(notificationStreamService).sendNotification(argThat(received ->
                received.data().get("title").asString().equals("친구의 생일이 7일 남았어요.")
                        && received.recipients().size() == 2
                        && received.recipients().getFirst().receiverUserId().equals(1L)
                        && received.recipients().getFirst().unreadCount() == 3L
                        && received.recipients().getLast().data().get("notificationId").asLong() == 11L));
    }

    @Test
    void malformedJson_onMessage_doesNotSend() {
        subscriber.onMessage(toMessage("{not-json"), CHANNEL);

        verify(notificationStreamService, never()).sendNotification(any());
    }

    @Test
    void recipientsMissing_onMessage_doesNotSend() {
        subscriber.onMessage(toMessage("{\"data\":{\"title\":\"알림\"}}"), CHANNEL);

        verify(notificationStreamService, never()).sendNotification(any());
    }

    @Test
    void receiverUserIdMissing_onMessage_doesNotSend() {
        subscriber.onMessage(toMessage(
                "{\"data\":{\"title\":\"알림\"},\"recipients\":[{\"data\":{\"notificationId\":10},\"unreadCount\":3}]}"
        ), CHANNEL);

        verify(notificationStreamService, never()).sendNotification(any());
    }

    @Test
    void notificationIdMissing_onMessage_doesNotSend() {
        subscriber.onMessage(toMessage(
                "{\"data\":{\"title\":\"알림\"},\"recipients\":[{\"receiverUserId\":1,\"data\":{},\"unreadCount\":3}]}"
        ), CHANNEL);

        verify(notificationStreamService, never()).sendNotification(any());
    }

    @Test
    void someRecipientsInvalid_onMessage_sendsOnlyValidRecipients() {
        subscriber.onMessage(toMessage(
                "{\"data\":{\"title\":\"알림\"},\"recipients\":["
                        + "{\"receiverUserId\":1,\"data\":{\"notificationId\":10},\"unreadCount\":3},"
                        + "{\"data\":{\"notificationId\":11},\"unreadCount\":1},"
                        + "{\"receiverUserId\":3,\"data\":{},\"unreadCount\":2}]}"
        ), CHANNEL);

        verify(notificationStreamService).sendNotification(argThat(received ->
                received.recipients().size() == 1
                        && received.recipients().getFirst().receiverUserId().equals(1L)));
    }

    @Test
    void commonDataNotObject_onMessage_doesNotSend() {
        subscriber.onMessage(toMessage(
                "{\"data\":\"알림\",\"recipients\":[{\"receiverUserId\":1,\"data\":{\"notificationId\":10},\"unreadCount\":3}]}"
        ), CHANNEL);

        verify(notificationStreamService, never()).sendNotification(any());
    }

    private Message toMessage(String body) {
        return new DefaultMessage(CHANNEL, body.getBytes(StandardCharsets.UTF_8));
    }
}
