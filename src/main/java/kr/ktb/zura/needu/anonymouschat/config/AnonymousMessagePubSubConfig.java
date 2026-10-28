package kr.ktb.zura.needu.anonymouschat.config;

import kr.ktb.zura.needu.anonymouschat.service.AnonymousMessageEventSubscriber;
import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

// 연속으로 보낸 메시지가 순서대로 전달되도록 한 스레드로 처리
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "needu.redis.pub-sub.enabled", matchIfMissing = true)
public class AnonymousMessagePubSubConfig {

    @Bean(autowireCandidate = false)
    public RedisMessageListenerContainer anonymousMessageRedisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            AnonymousMessageEventSubscriber subscriber,
            RedisKeyGenerator keyGenerator
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setTaskExecutor(createListenerExecutor());
        String channel = keyGenerator.generate("anonymouschat", "message-events");
        container.addMessageListener(subscriber, new ChannelTopic(channel));
        return container;
    }

    private ThreadPoolTaskExecutor createListenerExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setThreadNamePrefix("anonymous-message-pubsub-");
        executor.setDaemon(true);
        executor.initialize();
        return executor;
    }
}
