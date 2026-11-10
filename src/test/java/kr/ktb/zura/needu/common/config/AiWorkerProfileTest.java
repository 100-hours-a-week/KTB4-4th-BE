package kr.ktb.zura.needu.common.config;

import java.time.Clock;

import kr.ktb.zura.needu.aichat.config.AiReplyQueueConfig;
import kr.ktb.zura.needu.aichat.config.AiReplyQueueProperties;
import kr.ktb.zura.needu.aichat.facade.AiChatFacade;
import kr.ktb.zura.needu.aichat.facade.AiReplyWorker;
import kr.ktb.zura.needu.aichat.service.AiConversationLock;
import kr.ktb.zura.needu.aichat.service.AiGenerationStateService;
import kr.ktb.zura.needu.aichat.service.AiReplyJobReconcileScheduler;
import kr.ktb.zura.needu.aichat.service.AiReplyQueueMetricsScheduler;
import kr.ktb.zura.needu.aichat.service.AiReplyQueueService;
import kr.ktb.zura.needu.aichat.service.AiReplyTaskExecutor;
import kr.ktb.zura.needu.friend.service.FriendBirthdayNotificationScheduler;
import kr.ktb.zura.needu.friend.service.FriendBirthdayNotificationService;
import kr.ktb.zura.needu.friend.service.FriendSyncService;
import kr.ktb.zura.needu.friend.service.KakaoFriendSyncScheduler;
import kr.ktb.zura.needu.product.service.PurchaseCheckCreationService;
import kr.ktb.zura.needu.product.service.PurchaseCheckScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

// 프로필 파일이 business-policy.yml 기본값을 덮어쓰는지까지 보려고 설정 파일을 실제로 읽는다
class AiWorkerProfileTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withInitializer(context -> context.getBeanFactory()
                    .setConversionService(ApplicationConversionService.getSharedInstance()))
            .withUserConfiguration(AiReplyQueueConfig.class, AiReplyWorker.class, AiReplyJobReconcileScheduler.class,
                    AiReplyQueueMetricsScheduler.class, KakaoFriendSyncScheduler.class,
                    FriendBirthdayNotificationScheduler.class, PurchaseCheckScheduler.class)
            .withBean(AiChatFacade.class, () -> mock(AiChatFacade.class))
            .withBean(AiReplyQueueService.class, () -> mock(AiReplyQueueService.class))
            .withBean(AiReplyTaskExecutor.class, () -> mock(AiReplyTaskExecutor.class))
            .withBean(AiConversationLock.class, () -> mock(AiConversationLock.class))
            .withBean(AiGenerationStateService.class, () -> mock(AiGenerationStateService.class))
            .withBean(FriendSyncService.class, () -> mock(FriendSyncService.class))
            .withBean(FriendBirthdayNotificationService.class, () -> mock(FriendBirthdayNotificationService.class))
            .withBean(PurchaseCheckCreationService.class, () -> mock(PurchaseCheckCreationService.class))
            .withBean(Clock.class, Clock::systemUTC);

    @Test
    void defaultSettings_startContext_runsBatchSchedulersWithoutReplyWorker() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(AiReplyWorker.class);
            assertThat(context).doesNotHaveBean(AiReplyJobReconcileScheduler.class);
            assertThat(context).doesNotHaveBean(AiReplyQueueMetricsScheduler.class);
            assertThat(context).hasSingleBean(KakaoFriendSyncScheduler.class);
            assertThat(context).hasSingleBean(FriendBirthdayNotificationScheduler.class);
            assertThat(context).hasSingleBean(PurchaseCheckScheduler.class);
        });
    }

    // API 인스턴스: 작업을 스트림에 넣기만 하고 소비하지 않는다
    @Test
    void queueEnabledOnly_startContext_doesNotRunReplyWorker() {
        contextRunner
                .withPropertyValues("ai.chat.reply-queue.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(AiReplyQueueProperties.class).enabled()).isTrue();
                    assertThat(context).doesNotHaveBean(AiReplyWorker.class);
                    assertThat(context).doesNotHaveBean(AiReplyJobReconcileScheduler.class);
                    assertThat(context).doesNotHaveBean(AiReplyQueueMetricsScheduler.class);
                });
    }

    @Test
    void queueAndWorkerEnabled_startContext_runsReplyWorkerAndReconcile() {
        contextRunner
                .withPropertyValues("ai.chat.reply-queue.enabled=true", "ai.chat.reply-queue.worker-enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(AiReplyWorker.class).isRunning()).isTrue();
                    assertThat(context).hasSingleBean(AiReplyJobReconcileScheduler.class);
                    assertThat(context).hasSingleBean(AiReplyQueueMetricsScheduler.class);
                });
    }

    @Test
    void workerEnabledWithoutQueue_startContext_failsWithConfigError() {
        contextRunner
                .withPropertyValues("ai.chat.reply-queue.worker-enabled=true")
                .run(context -> assertThat(context).getFailure()
                        .rootCause()
                        .hasMessageContaining("ai.chat.reply-queue.worker-enabled requires ai.chat.reply-queue.enabled=true"));
    }

    @Test
    void aiWorkerProfile_startContext_runsOnlyReplyWorkerAndReconcile() {
        contextRunner
                .withPropertyValues("spring.profiles.active=" + AppProfile.AI_WORKER)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AiReplyQueueProperties properties = context.getBean(AiReplyQueueProperties.class);
                    assertThat(properties.enabled()).isTrue();
                    assertThat(properties.workerEnabled()).isTrue();
                    assertThat(context.getEnvironment().getProperty("needu.messaging.stream.worker.enabled"))
                            .isEqualTo("false");
                    assertThat(context.getEnvironment().getProperty("needu.redis.pub-sub.enabled")).isEqualTo("false");
                    assertThat(context).hasSingleBean(AiReplyWorker.class);
                    assertThat(context).hasSingleBean(AiReplyJobReconcileScheduler.class);
                    assertThat(context).doesNotHaveBean(KakaoFriendSyncScheduler.class);
                    assertThat(context).doesNotHaveBean(FriendBirthdayNotificationScheduler.class);
                    assertThat(context).doesNotHaveBean(PurchaseCheckScheduler.class);
                });
    }
}
