package kr.ktb.zura.needu.notification.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
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
                1L,
                jsonMapper.createObjectNode().put("notificationId", 10L).put("title", "친구가 콕 찔렀어요."),
                3L
        );

        subscriber.onMessage(toMessage(jsonMapper.writeValueAsString(event)), CHANNEL);

        verify(notificationStreamService).sendNotification(argThat(received ->
                received.receiverUserId().equals(1L)
                        && received.unreadCount() == 3L
                        && received.data().get("notificationId").asLong() == 10L));
    }

    @Test
    void malformedJson_onMessage_doesNotSend() {
        subscriber.onMessage(toMessage("{not-json"), CHANNEL);

        verify(notificationStreamService, never()).sendNotification(any());
    }

    @Test
    void receiverUserIdMissing_onMessage_doesNotSend() {
        subscriber.onMessage(toMessage("{\"data\":{\"notificationId\":10},\"unreadCount\":3}"), CHANNEL);

        verify(notificationStreamService, never()).sendNotification(any());
    }

    private Message toMessage(String body) {
        return new DefaultMessage(CHANNEL, body.getBytes(StandardCharsets.UTF_8));
    }
}
