package kr.ktb.zura.needu.friend.service;

import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class KakaoFriendSyncScheduler {

    private final FriendSyncService friendSyncService;

    public KakaoFriendSyncScheduler(FriendSyncService friendSyncService) {
        this.friendSyncService = friendSyncService;
    }

    @Scheduled(fixedDelayString = "${needu.friend.sync.fixed-delay}")
    @SchedulerLock(
            name = "kakaoFriendSync",
            lockAtMostFor = "${needu.friend.sync.lock-at-most-for}",
            lockAtLeastFor = "${needu.friend.sync.lock-at-least-for}"
    )
    public void syncPendingRequests() {
        LockAssert.assertLocked();
        friendSyncService.syncPendingRequests();
    }
}
