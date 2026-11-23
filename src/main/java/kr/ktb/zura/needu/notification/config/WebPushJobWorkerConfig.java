package kr.ktb.zura.needu.notification.config;

import kr.ktb.zura.needu.notification.service.WebPushDeliveryService;
import kr.ktb.zura.needu.notification.service.WebPushJobService;
import kr.ktb.zura.needu.notification.service.WebPushJobWorkerManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 웹푸시, 발송 작업 생성, 이 인스턴스의 워커가 모두 켜져 있을 때만 워커를 띄운다
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = {
                "notification.webpush.enabled",
                "needu.notification.webpush-job.enabled",
                "needu.notification.webpush-job.worker.enabled"
        },
        havingValue = "true"
)
@EnableConfigurationProperties(WebPushJobWorkerProperties.class)
public class WebPushJobWorkerConfig {

    @Bean
    public WebPushJobWorkerManager webPushJobWorkerManager(
            WebPushJobService webPushJobService,
            WebPushDeliveryService webPushDeliveryService,
            WebPushJobWorkerProperties webPushJobWorkerProperties
    ) {
        return new WebPushJobWorkerManager(webPushJobService, webPushDeliveryService, webPushJobWorkerProperties);
    }
}
