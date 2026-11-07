package kr.ktb.zura.needu.notification.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Map;
import java.util.Set;
import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;
import kr.ktb.zura.needu.notification.service.NotificationEventSubscriber;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.Topic;
import org.springframework.test.util.ReflectionTestUtils;

class NotificationPubSubConfigTest {

    @Test
    void notificationRedisMessageListenerContainer_registersSubscriberOnNotificationEventsChannel() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        NotificationEventSubscriber subscriber = mock(NotificationEventSubscriber.class);

        RedisMessageListenerContainer container = new NotificationPubSubConfig()
                .notificationRedisMessageListenerContainer(
                        connectionFactory, subscriber, new RedisKeyGenerator("backend:test"));

        assertThat(container.getConnectionFactory()).isSameAs(connectionFactory);
        @SuppressWarnings("unchecked")
        Map<MessageListener, Set<Topic>> listenerTopics =
                (Map<MessageListener, Set<Topic>>) ReflectionTestUtils.getField(container, "listenerTopics");
        assertThat(listenerTopics).containsOnlyKeys(subscriber);
        assertThat(listenerTopics.get(subscriber))
                .containsExactly(new ChannelTopic("backend:test:notification:events"));
    }
}
