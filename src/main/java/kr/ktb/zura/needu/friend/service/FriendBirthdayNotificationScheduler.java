package kr.ktb.zura.needu.friend.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FriendBirthdayNotificationScheduler {

    private final FriendBirthdayNotificationService notificationService;
    private final ZoneId notificationZone;
    private final int maxRetryCount;
    private final Duration retryDelay;

    public FriendBirthdayNotificationScheduler(
            FriendBirthdayNotificationService notificationService,
            @Value("${needu.notification.friend-birthday.zone}") String notificationZone,
            @Value("${needu.notification.friend-birthday.max-retry-count}") int maxRetryCount,
            @Value("${needu.notification.friend-birthday.retry-delay}") Duration retryDelay
    ) {
        if (maxRetryCount < 0) {
            throw new IllegalArgumentException(
                    "needu.notification.friend-birthday.max-retry-count must not be negative. value="
                            + maxRetryCount);
        }
        if (retryDelay.isNegative()) {
            throw new IllegalArgumentException(
                    "needu.notification.friend-birthday.retry-delay must not be negative. value=" + retryDelay);
        }
        this.notificationService = notificationService;
        this.notificationZone = ZoneId.of(notificationZone);
        this.maxRetryCount = maxRetryCount;
        this.retryDelay = retryDelay;
    }

    @Scheduled(
            cron = "${needu.notification.friend-birthday.cron}",
            zone = "${needu.notification.friend-birthday.zone}"
    )
    @SchedulerLock(
            name = "friendBirthdayNotification",
            lockAtMostFor = "${needu.notification.friend-birthday.lock-at-most-for}",
            lockAtLeastFor = "${needu.notification.friend-birthday.lock-at-least-for}"
    )
    public void createNotifications() {
        LockAssert.assertLocked();
        LocalDate executionDate = LocalDate.now(notificationZone);
        for (int retryCount = 0; ; retryCount++) {
            try {
                notificationService.createNotifications(executionDate);
                return;
            } catch (RuntimeException exception) {
                if (retryCount >= maxRetryCount) {
                    log.error("Friend birthday notification batch retry exhausted. executionDate={}, retryCount={}",
                            executionDate, retryCount, exception);
                    throw exception;
                }
                log.warn("Retrying friend birthday notification batch. executionDate={}, retryCount={}",
                        executionDate, retryCount + 1, exception);
                waitForRetry();
            }
        }
    }

    private void waitForRetry() {
        try {
            Thread.sleep(retryDelay.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Friend birthday notification batch retry interrupted.", exception);
        }
    }
}
