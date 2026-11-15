package kr.ktb.zura.needu.notification.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
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
        deliver(notifications, Runnable::run);
    }

    // 구독별 발송을 sendExecutor로 나눠 보내고 모두 끝날 때까지 기다린다
    public void deliver(List<NotificationsCreatedEvent.CreatedNotification> notifications, Executor sendExecutor) {
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

        List<CompletableFuture<Void>> sends = new ArrayList<>();
        notifications.forEach(notification -> subscriptionsByUserId
                .getOrDefault(notification.receiverUserId(), List.of())
                .forEach(subscription -> sends.add(
                        CompletableFuture.runAsync(() -> send(subscription, notification), sendExecutor))));
        CompletableFuture.allOf(sends.toArray(CompletableFuture[]::new)).join();
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
