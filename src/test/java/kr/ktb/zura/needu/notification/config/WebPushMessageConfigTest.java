package kr.ktb.zura.needu.notification.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import kr.ktb.zura.needu.common.messaging.MessageSubscriber;
import kr.ktb.zura.needu.notification.service.WebPushMessageConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class WebPushMessageConfigTest {

    private final MessageSubscriber messageSubscriber = mock(MessageSubscriber.class);
    private final WebPushMessageConsumer webPushMessageConsumer = mock(WebPushMessageConsumer.class);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(MessageSubscriber.class, () -> messageSubscriber)
            .withBean(WebPushMessageConsumer.class, () -> webPushMessageConsumer)
            .withUserConfiguration(WebPushMessageConfig.class);

    @Test
    void subscribesWebPushConsumerWhenWebPushEnabled() {
        contextRunner
                .withPropertyValues("notification.webpush.enabled=true")
                .run(context -> {
                    assertThat(context).hasBean("webPushMessageSubscription");
                    verify(messageSubscriber).subscribe(webPushMessageConsumer);
                });
    }

    @Test
    void doesNotSubscribeWhenWebPushDisabled() {
        contextRunner
                .withPropertyValues("notification.webpush.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean("webPushMessageSubscription");
                    verifyNoInteractions(messageSubscriber);
                });
    }
}
