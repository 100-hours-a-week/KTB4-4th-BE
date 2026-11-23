package kr.ktb.zura.needu.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class WebPushNotificationCreatedEventListenerConditionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(WebPushDeliveryService.class, () -> mock(WebPushDeliveryService.class))
            .withUserConfiguration(WebPushNotificationCreatedEventListener.class);

    @Test
    void webPushEnabledAndJobQueueDisabled_registersDirectSendListener() {
        contextRunner
                .withPropertyValues(
                        "notification.webpush.enabled=true",
                        "needu.notification.webpush-job.enabled=false")
                .run(context -> assertThat(context).hasSingleBean(WebPushNotificationCreatedEventListener.class));
    }

    @Test
    void webPushEnabledAndJobQueueMissing_registersDirectSendListener() {
        contextRunner
                .withPropertyValues("notification.webpush.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(WebPushNotificationCreatedEventListener.class));
    }

    @Test
    void webPushEnabledAndJobQueueEnabled_doesNotRegisterDirectSendListener() {
        contextRunner
                .withPropertyValues(
                        "notification.webpush.enabled=true",
                        "needu.notification.webpush-job.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(WebPushNotificationCreatedEventListener.class));
    }

    @Test
    void webPushDisabled_doesNotRegisterDirectSendListener() {
        contextRunner
                .withPropertyValues("notification.webpush.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(WebPushNotificationCreatedEventListener.class));
    }
}
