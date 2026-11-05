package kr.ktb.zura.needu.friend.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.LocalDate;
import net.javacrumbs.shedlock.core.LockAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FriendBirthdayNotificationSchedulerTest {

    private final FriendBirthdayNotificationService notificationService =
            mock(FriendBirthdayNotificationService.class);

    @AfterEach
    void tearDown() {
        LockAssert.TestHelper.makeAllAssertsPass(false);
    }

    @Test
    void batchFailedOnce_createNotifications_retriesWithSameExecutionDate() {
        FriendBirthdayNotificationScheduler scheduler = createScheduler(2);
        LockAssert.TestHelper.makeAllAssertsPass(true);
        doThrow(new IllegalStateException("batch failed"))
                .doNothing()
                .when(notificationService).createNotifications(any(LocalDate.class));

        scheduler.createNotifications();

        ArgumentCaptor<LocalDate> executionDateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(notificationService, times(2)).createNotifications(executionDateCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(executionDateCaptor.getAllValues()).hasSize(2).allMatch(
                executionDateCaptor.getAllValues().getFirst()::equals);
    }

    @Test
    void batchKeepsFailing_createNotifications_throwsAfterMaxRetryCount() {
        FriendBirthdayNotificationScheduler scheduler = createScheduler(2);
        LockAssert.TestHelper.makeAllAssertsPass(true);
        doThrow(new IllegalStateException("batch failed"))
                .when(notificationService).createNotifications(any(LocalDate.class));

        assertThatThrownBy(scheduler::createNotifications)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("batch failed");

        verify(notificationService, times(3)).createNotifications(any(LocalDate.class));
    }

    @Test
    void missingLock_createNotifications_rejectsExecution() {
        FriendBirthdayNotificationScheduler scheduler = createScheduler(2);

        assertThatThrownBy(scheduler::createNotifications)
                .isInstanceOf(IllegalStateException.class);

        verify(notificationService, never()).createNotifications(any());
    }

    private FriendBirthdayNotificationScheduler createScheduler(int maxRetryCount) {
        return new FriendBirthdayNotificationScheduler(
                notificationService, "Asia/Seoul", maxRetryCount, Duration.ZERO);
    }
}
