package kr.ktb.zura.needu.notification.service;

import kr.ktb.zura.needu.common.messaging.MessageConsumer;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.webpush.enabled", havingValue = "true")
public class WebPushMessageConsumer implements MessageConsumer<WebPushMessage> {

    private final WebPushDeliveryService webPushDeliveryService;

    @Override
    public String topic() {
        return WebPushMessage.TOPIC;
    }

    @Override
    public Class<WebPushMessage> payloadType() {
        return WebPushMessage.class;
    }

    // 구독 조회 실패는 전파해 메시지를 ACK하지 않고 pending으로 남긴다
    @Override
    public void consume(WebPushMessage message) {
        webPushDeliveryService.deliver(message.toNotifications());
    }
}
