package kr.ktb.zura.needu.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.config.TaskManagementConfigUtils;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class AsyncConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AsyncConfig.class)
            .withPropertyValues(
                    "needu.async.ai.core-pool-size=4",
                    "needu.async.ai.max-pool-size=8",
                    "needu.async.ai.queue-capacity=50",
                    "needu.async.notification.core-pool-size=2",
                    "needu.async.notification.max-pool-size=4",
                    "needu.async.notification.queue-capacity=100"
            );

    @Test
    void asyncConfig_registersPurposeSpecificExecutors() {
        contextRunner.run(context -> {
            ThreadPoolTaskExecutor aiExecutor = context.getBean(
                    "aiTaskExecutor", ThreadPoolTaskExecutor.class);
            ThreadPoolTaskExecutor notificationExecutor = context.getBean(
                    "notificationTaskExecutor", ThreadPoolTaskExecutor.class);

            assertThat(context).hasBean(TaskManagementConfigUtils.ASYNC_ANNOTATION_PROCESSOR_BEAN_NAME);
            assertThat(aiExecutor.getCorePoolSize()).isEqualTo(4);
            assertThat(aiExecutor.getMaxPoolSize()).isEqualTo(8);
            assertThat(aiExecutor.getQueueCapacity()).isEqualTo(50);
            assertThat(aiExecutor.getThreadNamePrefix()).isEqualTo("ai-task-");

            assertThat(notificationExecutor.getCorePoolSize()).isEqualTo(2);
            assertThat(notificationExecutor.getMaxPoolSize()).isEqualTo(4);
            assertThat(notificationExecutor.getQueueCapacity()).isEqualTo(100);
            assertThat(notificationExecutor.getThreadNamePrefix()).isEqualTo("notification-task-");
        });
    }
}
