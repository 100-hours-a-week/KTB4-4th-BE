package kr.ktb.zura.needu.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

class RedisPubSubConfigTest {

    @Test
    void redisMessageListenerContainer_usesConfiguredConnectionFactory() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);

        RedisMessageListenerContainer container =
                new RedisPubSubConfig().redisMessageListenerContainer(connectionFactory);

        assertThat(container.getConnectionFactory()).isSameAs(connectionFactory);
    }
}
