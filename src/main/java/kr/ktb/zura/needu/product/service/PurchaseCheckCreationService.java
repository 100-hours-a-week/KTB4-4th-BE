package kr.ktb.zura.needu.product.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import kr.ktb.zura.needu.product.repository.ProductLinkClickRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PurchaseCheckCreationService {

    private final ProductLinkClickRepository productLinkClickRepository;
    private final PurchaseCheckService purchaseCheckService;
    private final Duration checkDelay;
    private final Duration lookback;
    private final int batchSize;

    public PurchaseCheckCreationService(
            ProductLinkClickRepository productLinkClickRepository,
            PurchaseCheckService purchaseCheckService,
            @Value("${needu.product.purchase-check.delay}") Duration checkDelay,
            @Value("${needu.product.purchase-check.lookback}") Duration lookback,
            @Value("${needu.product.purchase-check.batch-size}") int batchSize
    ) {
        this.productLinkClickRepository = productLinkClickRepository;
        this.purchaseCheckService = purchaseCheckService;
        this.checkDelay = checkDelay;
        this.lookback = lookback;
        this.batchSize = batchSize;
    }

    public void createPurchaseChecks(LocalDateTime now) {
        LocalDateTime to = now.minus(checkDelay);
        LocalDateTime from = to.minus(lookback);
        long lastId = 0L;
        int createdCount = 0;
        int failedCount = 0;

        List<Long> clickIds;
        do {
            clickIds = productLinkClickRepository.findPurchaseCheckTargetIds(from, to, lastId, Limit.of(batchSize));
            for (Long clickId : clickIds) {
                try {
                    purchaseCheckService.createPurchaseCheck(clickId);
                    createdCount++;
                } catch (RuntimeException e) {
                    failedCount++;
                    log.warn("Purchase check creation failed. productLinkClickId={}", clickId, e);
                }
            }
            if (!clickIds.isEmpty()) {
                lastId = clickIds.getLast();
            }
        } while (clickIds.size() == batchSize);

        log.info("Purchase check creation finished. createdCount={}, failedCount={}", createdCount, failedCount);
    }
}
