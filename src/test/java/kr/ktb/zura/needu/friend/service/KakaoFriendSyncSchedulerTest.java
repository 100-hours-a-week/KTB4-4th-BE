package kr.ktb.zura.needu.friend.service;

import net.javacrumbs.shedlock.core.LockAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class KakaoFriendSyncSchedulerTest {

    @AfterEach
    void tearDown() {
        LockAssert.TestHelper.makeAllAssertsPass(false);
    }

    @Test
    void acquiredLock_syncPendingRequests_callsService() {
        FriendSyncService friendSyncService = mock(FriendSyncService.class);
        KakaoFriendSyncScheduler scheduler = new KakaoFriendSyncScheduler(friendSyncService);
        LockAssert.TestHelper.makeAllAssertsPass(true);

        scheduler.syncPendingRequests();

        verify(friendSyncService).syncPendingRequests();
    }

    @Test
    void missingLock_syncPendingRequests_rejectsExecution() {
        FriendSyncService friendSyncService = mock(FriendSyncService.class);
        KakaoFriendSyncScheduler scheduler = new KakaoFriendSyncScheduler(friendSyncService);

        assertThatThrownBy(scheduler::syncPendingRequests)
                .isInstanceOf(IllegalStateException.class);
        verify(friendSyncService, never()).syncPendingRequests();
    }
}
