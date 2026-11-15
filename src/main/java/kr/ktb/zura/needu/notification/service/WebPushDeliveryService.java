package kr.ktb.zura.needu.notification.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import kr.ktb.zura.needu.notification.client.WebPushClient;
import kr.ktb.zura.needu.notification.client.WebPushResult;
import kr.ktb.zura.needu.notification.entity.PushSubscription;
import kr.ktb.zura.needu.notification.repository.PushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.webpush.enabled", havingValue = "true")
public class WebPushDeliveryService {

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final WebPushClient webPushClient;

    public void deliver(List<NotificationsCreatedEvent.CreatedNotification> notifications) {
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
