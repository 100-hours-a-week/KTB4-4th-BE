package kr.ktb.zura.needu.notification.service;

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
@ConditionalOnProperty(name = "needu.notification.webpush-job.enabled", havingValue = "false", matchIfMissing = true)
public class WebPushNotificationCreatedEventListener {

    private final WebPushDeliveryService webPushDeliveryService;

    @Async("notificationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendWebPush(NotificationsCreatedEvent event) {
        try {
            webPushDeliveryService.deliver(event.notifications());
        } catch (RuntimeException exception) {
            log.warn("Web push subscription lookup failed after notification creation.", exception);
        }
    }
}
