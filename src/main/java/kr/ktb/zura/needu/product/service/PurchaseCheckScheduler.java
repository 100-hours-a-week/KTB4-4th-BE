package kr.ktb.zura.needu.product.service;

import java.time.LocalDateTime;

import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PurchaseCheckScheduler {

    private final PurchaseCheckCreationService purchaseCheckCreationService;

    public PurchaseCheckScheduler(PurchaseCheckCreationService purchaseCheckCreationService) {
        this.purchaseCheckCreationService = purchaseCheckCreationService;
    }

    @Scheduled(
            cron = "${needu.product.purchase-check.cron}",
            zone = "${needu.product.purchase-check.zone}"
    )
    @SchedulerLock(
            name = "purchaseCheckCreation",
            lockAtMostFor = "${needu.product.purchase-check.lock-at-most-for}",
            lockAtLeastFor = "${needu.product.purchase-check.lock-at-least-for}"
    )
    public void createPurchaseChecks() {
        LockAssert.assertLocked();
        purchaseCheckCreationService.createPurchaseChecks(LocalDateTime.now());
    }
}
