package kr.ktb.zura.needu.notification.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import kr.ktb.zura.needu.common.messaging.MessageConsumer;
import kr.ktb.zura.needu.notification.client.WebPushClient;
import kr.ktb.zura.needu.notification.client.WebPushResult;
import kr.ktb.zura.needu.notification.entity.PushSubscription;
import kr.ktb.zura.needu.notification.repository.PushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// 웹푸시 작업 큐 메시지를 받아 수신자의 구독마다 푸시를 보낸다.
// 구독 조회가 실패하면 아직 아무것도 보내지 않았으므로 예외를 던져 다시 처리되게 한다.
// 구독 하나의 발송 실패는 기록만 하고 넘어간다 => 메시지 전체를 다시 처리하면 이미 받은 사용자에게 중복 발송된다
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.webpush.enabled", havingValue = "true")
public class WebPushMessageConsumer implements MessageConsumer<WebPushMessage> {

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final WebPushClient webPushClient;

    @Override
    public String topic() {
        return WebPushMessage.TOPIC;
    }

    @Override
    public Class<WebPushMessage> payloadType() {
        return WebPushMessage.class;
    }

    @Override
    public void consume(WebPushMessage message) {
        List<NotificationsCreatedEvent.CreatedNotification> notifications = message.toNotifications();
        if (notifications.isEmpty()) {
            return;
        }

        Map<Long, List<PushSubscription>> subscriptionsByUserId = pushSubscriptionRepository
                .findAllByUserIdIn(notifications.stream()
                        .map(NotificationsCreatedEvent.CreatedNotification::receiverUserId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.groupingBy(PushSubscription::getUserId));

        notifications.forEach(notification -> subscriptionsByUserId
                .getOrDefault(notification.receiverUserId(), List.of())
                .forEach(subscription -> send(subscription, notification)));
    }

    private void send(PushSubscription subscription, NotificationsCreatedEvent.CreatedNotification notification) {
        try {
            WebPushResult result = webPushClient.send(subscription, notification);
            if (result == WebPushResult.EXPIRED) {
                pushSubscriptionRepository.deleteById(subscription.getId());
            } else if (result == WebPushResult.FAILED) {
                log.warn("Web push publish failed. notificationId={}, receiverUserId={}, subscriptionId={}",
                        notification.notificationId(), notification.receiverUserId(), subscription.getId());
            }
        } catch (RuntimeException exception) {
            log.warn("Web push publish failed. notificationId={}, receiverUserId={}, subscriptionId={}",
                    notification.notificationId(), notification.receiverUserId(), subscription.getId(), exception);
        }
    }
}
