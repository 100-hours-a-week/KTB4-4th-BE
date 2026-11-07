package kr.ktb.zura.needu.notification.config;

import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;
import kr.ktb.zura.needu.common.redis.StreamRedisConfig;
import kr.ktb.zura.needu.notification.service.NotificationEventSubscriber;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

// 생일 배치처럼 대량으로 몰리는 알림 이벤트는 캐싱용 Redis와 부딪히지 않게 스트림용 Redis로 주고받는다.
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "needu.redis.pub-sub.enabled", matchIfMissing = true)
public class NotificationPubSubConfig {

    @Bean(autowireCandidate = false)
    public RedisMessageListenerContainer notificationRedisMessageListenerContainer(
            @Qualifier(StreamRedisConfig.STREAM_REDIS_CONNECTION_FACTORY) RedisConnectionFactory connectionFactory,
            NotificationEventSubscriber subscriber,
            RedisKeyGenerator keyGenerator
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        String channel = keyGenerator.generate("notification", "events");
        container.addMessageListener(subscriber, new ChannelTopic(channel));
        return container;
    }
}
