package kr.ktb.zura.needu.aichat.config;

import kr.ktb.zura.needu.aichat.service.AiChatEventSubscriber;
import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

// AI 채팅 이벤트 채널과 Subscriber를 공통 Redis ListenerContainer에 등록한다.
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "needu.redis.pub-sub.enabled", matchIfMissing = true)
public class AiChatPubSubConfig {

    public AiChatPubSubConfig(
            RedisMessageListenerContainer container,
            AiChatEventSubscriber subscriber,
            RedisKeyGenerator keyGenerator
    ) {
        String channel = keyGenerator.generate("aichat", "events");
        container.addMessageListener(subscriber, new ChannelTopic(channel));
    }
}
