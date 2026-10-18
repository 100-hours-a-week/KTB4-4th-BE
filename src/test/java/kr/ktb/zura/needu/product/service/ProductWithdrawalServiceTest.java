package kr.ktb.zura.needu.product.service;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import kr.ktb.zura.needu.product.repository.GiftProductRepository;
import kr.ktb.zura.needu.product.repository.PersonalProductRepository;
import kr.ktb.zura.needu.product.repository.ProductFeedbackRepository;
import kr.ktb.zura.needu.product.repository.ProductLinkClickRepository;
import kr.ktb.zura.needu.product.repository.PurchaseCheckRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class ProductWithdrawalServiceTest {

    @Test
    void relatedProductDataExists_deleteUserData_deletesChecksBeforeClicks() {
        PurchaseCheckRepository purchaseCheckRepository = mock(PurchaseCheckRepository.class);
        ProductLinkClickRepository linkClickRepository = mock(ProductLinkClickRepository.class);
        PersonalProductRepository personalProductRepository = mock(PersonalProductRepository.class);
        GiftProductRepository giftProductRepository = mock(GiftProductRepository.class);
        ProductFeedbackRepository feedbackRepository = mock(ProductFeedbackRepository.class);
        ProductWithdrawalService service = new ProductWithdrawalService(
                purchaseCheckRepository,
                linkClickRepository,
                personalProductRepository,
                giftProductRepository,
                feedbackRepository
        );

        service.deleteUserData(1L);

        InOrder order = inOrder(
                purchaseCheckRepository,
                linkClickRepository,
                personalProductRepository,
                giftProductRepository,
                feedbackRepository
        );
        order.verify(purchaseCheckRepository).deleteAllRelatedToUserId(1L);
        order.verify(linkClickRepository).deleteAllRelatedToUserId(1L);
        order.verify(personalProductRepository).deleteAllByUserId(1L);
        order.verify(giftProductRepository).deleteAllByUserId(1L);
        order.verify(feedbackRepository).deleteAllByUserId(1L);
    }
}
