package kr.ktb.zura.needu.notification.config;

import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;
import kr.ktb.zura.needu.notification.service.NotificationEventSubscriber;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

// 알림 이벤트 채널과 Subscriber를 공통 Redis ListenerContainer에 등록한다.
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "needu.redis.pub-sub.enabled", matchIfMissing = true)
public class NotificationPubSubConfig {

    public NotificationPubSubConfig(
            RedisMessageListenerContainer container,
            NotificationEventSubscriber subscriber,
            RedisKeyGenerator keyGenerator
    ) {
        String channel = keyGenerator.generate("notification", "events");
        container.addMessageListener(subscriber, new ChannelTopic(channel));
    }
}
