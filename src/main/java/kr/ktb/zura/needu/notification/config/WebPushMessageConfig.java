package kr.ktb.zura.needu.notification.config;

import kr.ktb.zura.needu.common.messaging.MessageConsumerRegistry;
import kr.ktb.zura.needu.notification.service.WebPushMessageConsumer;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "notification.webpush.enabled", havingValue = "true")
public class WebPushMessageConfig {

    @Bean
    public SmartInitializingSingleton webPushMessageConsumerRegistration(
            MessageConsumerRegistry messageConsumerRegistry,
            WebPushMessageConsumer webPushMessageConsumer
    ) {
        return () -> messageConsumerRegistry.register(webPushMessageConsumer);
    }
}
