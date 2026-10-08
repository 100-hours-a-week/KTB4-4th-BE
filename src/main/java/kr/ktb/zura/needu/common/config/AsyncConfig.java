package kr.ktb.zura.needu.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@EnableAsync
@Configuration(proxyBeanMethods = false)
public class AsyncConfig {

    @Bean("aiTaskExecutor")
    public ThreadPoolTaskExecutor aiTaskExecutor(
            @Value("${needu.async.ai.core-pool-size:4}") int corePoolSize,
            @Value("${needu.async.ai.max-pool-size:8}") int maxPoolSize,
            @Value("${needu.async.ai.queue-capacity:50}") int queueCapacity
    ) {
        return createTaskExecutor(corePoolSize, maxPoolSize, queueCapacity, "ai-task-");
    }

    @Bean("notificationTaskExecutor")
    public ThreadPoolTaskExecutor notificationTaskExecutor(
            @Value("${needu.async.notification.core-pool-size:2}") int corePoolSize,
            @Value("${needu.async.notification.max-pool-size:4}") int maxPoolSize,
            @Value("${needu.async.notification.queue-capacity:100}") int queueCapacity
    ) {
        return createTaskExecutor(corePoolSize, maxPoolSize, queueCapacity, "notification-task-");
    }

    private ThreadPoolTaskExecutor createTaskExecutor(
            int corePoolSize,
            int maxPoolSize,
            int queueCapacity,
            String threadNamePrefix
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        return executor;
    }
}
