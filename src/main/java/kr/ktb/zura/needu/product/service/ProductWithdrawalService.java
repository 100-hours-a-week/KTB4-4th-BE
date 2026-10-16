package kr.ktb.zura.needu.product.service;

import kr.ktb.zura.needu.product.repository.GiftProductRepository;
import kr.ktb.zura.needu.product.repository.PersonalProductRepository;
import kr.ktb.zura.needu.product.repository.ProductFeedbackRepository;
import kr.ktb.zura.needu.product.repository.ProductLinkClickRepository;
import kr.ktb.zura.needu.product.repository.PurchaseCheckRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductWithdrawalService {

    private final PurchaseCheckRepository purchaseCheckRepository;
    private final ProductLinkClickRepository productLinkClickRepository;
    private final PersonalProductRepository personalProductRepository;
    private final GiftProductRepository giftProductRepository;
    private final ProductFeedbackRepository productFeedbackRepository;

    @Transactional
    public void deleteUserData(Long userId) {
        purchaseCheckRepository.deleteAllRelatedToUserId(userId);
        productLinkClickRepository.deleteAllRelatedToUserId(userId);
        personalProductRepository.deleteAllByUserId(userId);
        giftProductRepository.deleteAllByUserId(userId);
        productFeedbackRepository.deleteAllByUserId(userId);
    }
}
