package kr.ktb.zura.needu.friend.service;

import java.time.LocalDate;
import java.time.ZoneId;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FriendBirthdayNotificationScheduler {

    private final FriendBirthdayNotificationService notificationService;
    private final ZoneId notificationZone;

    public FriendBirthdayNotificationScheduler(
            FriendBirthdayNotificationService notificationService,
            @Value("${needu.notification.friend-birthday.zone}") String notificationZone
    ) {
        this.notificationService = notificationService;
        this.notificationZone = ZoneId.of(notificationZone);
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
        notificationService.createNotifications(LocalDate.now(notificationZone));
    }
}
