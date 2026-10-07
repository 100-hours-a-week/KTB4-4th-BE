package kr.ktb.zura.needu.product.service;

import kr.ktb.zura.needu.product.dto.response.PurchaseCheckResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PurchaseCheckService {

    // TODO: 내 구매 확인이 아니면 PURCHASE_CHECK_NOT_FOUND, 이미 답했으면 PURCHASE_CHECK_ALREADY_ANSWERED.
    //  구매 확인을 만들고 알림으로 묻는 시점은 일주일 뒤
    @Transactional
    public PurchaseCheckResponse answerPurchaseCheck(Long userId, Long purchaseCheckId, boolean purchased) {
        throw new UnsupportedOperationException("미구현");
    }
}
