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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.webpush.enabled", havingValue = "true")
public class WebPushNotificationCreatedEventListener {

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final WebPushClient webPushClient;

    @Async("notificationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendWebPush(NotificationsCreatedEvent event) {
        if (event.notifications().isEmpty()) {
            return;
        }

        Map<Long, List<PushSubscription>> subscriptionsByUserId;
        try {
            subscriptionsByUserId = pushSubscriptionRepository.findAllByUserIdIn(event.notifications().stream()
                            .map(NotificationsCreatedEvent.CreatedNotification::receiverUserId)
                            .distinct()
                            .toList())
                    .stream()
                    .collect(Collectors.groupingBy(PushSubscription::getUserId));
        } catch (RuntimeException exception) {
            log.warn("Web push subscription lookup failed after notification creation.", exception);
            return;
        }

        event.notifications().forEach(notification -> subscriptionsByUserId
                .getOrDefault(notification.receiverUserId(), List.of())
                .forEach(subscription -> send(webPushClient, subscription, notification)));
    }

    private void send(
            WebPushClient webPushClient,
            PushSubscription subscription,
            NotificationsCreatedEvent.CreatedNotification notification
    ) {
        try {
            WebPushResult result = webPushClient.send(subscription, notification);
            if (result == WebPushResult.EXPIRED) {
                pushSubscriptionRepository.deleteById(subscription.getId());
            } else if (result == WebPushResult.FAILED) {
                log.warn(
                        "Web push publish failed. notificationId={}, receiverUserId={}, subscriptionId={}",
                        notification.notificationId(),
                        notification.receiverUserId(),
                        subscription.getId()
                );
            }
        } catch (RuntimeException exception) {
            log.warn(
                    "Web push publish failed. notificationId={}, receiverUserId={}, subscriptionId={}",
                    notification.notificationId(),
                    notification.receiverUserId(),
                    subscription.getId(),
                    exception
            );
        }
    }
}
