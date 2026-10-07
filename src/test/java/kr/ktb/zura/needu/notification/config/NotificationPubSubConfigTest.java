package kr.ktb.zura.needu.notification.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;
import kr.ktb.zura.needu.notification.service.NotificationEventSubscriber;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

class NotificationPubSubConfigTest {

    @Test
    void create_registersSubscriberOnNotificationEventsChannel() {
        RedisMessageListenerContainer container = mock(RedisMessageListenerContainer.class);
        NotificationEventSubscriber subscriber = mock(NotificationEventSubscriber.class);

        new NotificationPubSubConfig(container, subscriber, new RedisKeyGenerator("needu:test"));

        verify(container).addMessageListener(
                subscriber,
                new ChannelTopic("needu:test:notification:events")
        );
    }
}
