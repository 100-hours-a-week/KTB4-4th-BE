package kr.ktb.zura.needu.notification.config;

import kr.ktb.zura.needu.common.messaging.MessageSubscriber;
import kr.ktb.zura.needu.notification.service.WebPushMessageConsumer;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 웹푸시가 켜져 있으면 웹푸시 작업 큐를 구독한다.
// 모든 빈이 만들어진 뒤 등록한다 => Subscriber가 기동(start)할 때 webpush 워커가 함께 시작된다
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "notification.webpush.enabled", havingValue = "true")
public class WebPushMessageConfig {

    @Bean
    public SmartInitializingSingleton webPushMessageSubscription(
            MessageSubscriber messageSubscriber,
            WebPushMessageConsumer webPushMessageConsumer
    ) {
        return () -> messageSubscriber.subscribe(webPushMessageConsumer);
    }
}
