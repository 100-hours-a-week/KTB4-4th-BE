package kr.ktb.zura.needu.anonymouschat.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Map;
import java.util.Set;
import kr.ktb.zura.needu.anonymouschat.service.AnonymousMessageEventSubscriber;
import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.Topic;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

class AnonymousMessagePubSubConfigTest {

    private final RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
    private final AnonymousMessageEventSubscriber subscriber = mock(AnonymousMessageEventSubscriber.class);

    @Test
    void listenerContainer_registersSubscriberOnMessageEventsChannel() {
        RedisMessageListenerContainer container = createContainer();

        assertThat(container.getConnectionFactory()).isSameAs(connectionFactory);
        @SuppressWarnings("unchecked")
        Map<MessageListener, Set<Topic>> listenerTopics =
                (Map<MessageListener, Set<Topic>>) ReflectionTestUtils.getField(container, "listenerTopics");
        assertThat(listenerTopics).containsOnlyKeys(subscriber);
        assertThat(listenerTopics.get(subscriber))
                .containsExactly(new ChannelTopic("backend:test:anonymouschat:message-events"));
    }

    @Test
    void listenerContainer_handlesMessagesOnSingleThreadToKeepOrder() {
        RedisMessageListenerContainer container = createContainer();

        assertThat(ReflectionTestUtils.getField(container, "taskExecutor"))
                .isInstanceOfSatisfying(ThreadPoolTaskExecutor.class, executor ->
                        assertThat(executor.getMaxPoolSize()).isEqualTo(1));
    }

    private RedisMessageListenerContainer createContainer() {
        return new AnonymousMessagePubSubConfig().anonymousMessageRedisMessageListenerContainer(
                connectionFactory, subscriber, new RedisKeyGenerator("backend:test"));
    }
}
