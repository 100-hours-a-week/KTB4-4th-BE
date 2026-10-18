package kr.ktb.zura.needu.aichat.config;

import kr.ktb.zura.needu.aichat.service.AiChatEventSubscriber;
import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "needu.redis.pub-sub.enabled", matchIfMissing = true)
public class AiChatPubSubConfig {

    @Bean(autowireCandidate = false)
    public RedisMessageListenerContainer aiChatRedisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            AiChatEventSubscriber subscriber,
            RedisKeyGenerator keyGenerator
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setTaskExecutor(createListenerExecutor());
        String channel = keyGenerator.generate("aichat", "events");
        container.addMessageListener(subscriber, new ChannelTopic(channel));
        return container;
    }

    private ThreadPoolTaskExecutor createListenerExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setThreadNamePrefix("aichat-pubsub-");
        executor.setDaemon(true);
        executor.initialize();
        return executor;
    }
}
