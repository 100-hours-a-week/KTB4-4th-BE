package kr.ktb.zura.needu.product.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import kr.ktb.zura.needu.product.repository.ProductLinkClickRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PurchaseCheckCreationServiceTest {

    private static final int BATCH_SIZE = 2;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 15, 3, 0);
    private static final LocalDateTime TO = NOW.minusDays(7);
    private static final LocalDateTime FROM = TO.minusDays(3);

    private ProductLinkClickRepository productLinkClickRepository;
    private PurchaseCheckService purchaseCheckService;
    private PurchaseCheckCreationService creationService;

    @BeforeEach
    void setUp() {
        productLinkClickRepository = mock(ProductLinkClickRepository.class);
        purchaseCheckService = mock(PurchaseCheckService.class);
        creationService = new PurchaseCheckCreationService(
                productLinkClickRepository, purchaseCheckService,
                Duration.ofDays(7), Duration.ofDays(3), BATCH_SIZE);
    }

    @Test
    void fullBatch_createPurchaseChecks_readsNextBatchAfterLastId() {
        given(productLinkClickRepository.findPurchaseCheckTargetIds(FROM, TO, 0L, Limit.of(BATCH_SIZE)))
                .willReturn(List.of(1L, 2L));
        given(productLinkClickRepository.findPurchaseCheckTargetIds(FROM, TO, 2L, Limit.of(BATCH_SIZE)))
                .willReturn(List.of(5L));

        creationService.createPurchaseChecks(NOW);

        verify(purchaseCheckService).createPurchaseCheck(1L);
        verify(purchaseCheckService).createPurchaseCheck(2L);
        verify(purchaseCheckService).createPurchaseCheck(5L);
    }

    @Test
    void failedClick_createPurchaseChecks_continuesWithRemainingClicks() {
        given(productLinkClickRepository.findPurchaseCheckTargetIds(FROM, TO, 0L, Limit.of(BATCH_SIZE)))
                .willReturn(List.of(1L));
        willThrow(new IllegalStateException("notification failed"))
                .given(purchaseCheckService).createPurchaseCheck(1L);

        creationService.createPurchaseChecks(NOW);

        verify(purchaseCheckService).createPurchaseCheck(1L);
    }

    @Test
    void failedClickInFullBatch_createPurchaseChecks_doesNotReadSameClickAgain() {
        given(productLinkClickRepository.findPurchaseCheckTargetIds(FROM, TO, 0L, Limit.of(BATCH_SIZE)))
                .willReturn(List.of(1L, 2L));
        given(productLinkClickRepository.findPurchaseCheckTargetIds(FROM, TO, 2L, Limit.of(BATCH_SIZE)))
                .willReturn(List.of());
        willThrow(new IllegalStateException("notification failed"))
                .given(purchaseCheckService).createPurchaseCheck(1L);

        creationService.createPurchaseChecks(NOW);

        verify(purchaseCheckService).createPurchaseCheck(1L);
        verify(purchaseCheckService).createPurchaseCheck(2L);
    }

    @Test
    void noTargetClick_createPurchaseChecks_createsNothing() {
        given(productLinkClickRepository.findPurchaseCheckTargetIds(FROM, TO, 0L, Limit.of(BATCH_SIZE)))
                .willReturn(List.of());

        creationService.createPurchaseChecks(NOW);

        verifyNoInteractions(purchaseCheckService);
    }
}
