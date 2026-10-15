package kr.ktb.zura.needu.product.service;

import java.time.LocalDateTime;

import net.javacrumbs.shedlock.core.LockAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PurchaseCheckSchedulerTest {

    @AfterEach
    void tearDown() {
        LockAssert.TestHelper.makeAllAssertsPass(false);
    }

    @Test
    void acquiredLock_createPurchaseChecks_callsService() {
        PurchaseCheckCreationService creationService = mock(PurchaseCheckCreationService.class);
        PurchaseCheckScheduler scheduler = new PurchaseCheckScheduler(creationService);
        LockAssert.TestHelper.makeAllAssertsPass(true);

        scheduler.createPurchaseChecks();

        verify(creationService).createPurchaseChecks(any(LocalDateTime.class));
    }

    @Test
    void missingLock_createPurchaseChecks_rejectsExecution() {
        PurchaseCheckCreationService creationService = mock(PurchaseCheckCreationService.class);
        PurchaseCheckScheduler scheduler = new PurchaseCheckScheduler(creationService);

        assertThatThrownBy(scheduler::createPurchaseChecks)
                .isInstanceOf(IllegalStateException.class);
        verify(creationService, never()).createPurchaseChecks(any());
    }
}
