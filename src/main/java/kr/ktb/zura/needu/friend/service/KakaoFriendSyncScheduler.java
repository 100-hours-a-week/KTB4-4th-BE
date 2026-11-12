package kr.ktb.zura.needu.friend.service;

import kr.ktb.zura.needu.common.config.AppProfile;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile(AppProfile.NOT_AI_WORKER)
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
