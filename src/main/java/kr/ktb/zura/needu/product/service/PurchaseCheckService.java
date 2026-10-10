package kr.ktb.zura.needu.product.service;

import kr.ktb.zura.needu.product.dto.response.PurchaseCheckResponse;
import kr.ktb.zura.needu.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PurchaseCheckService {

    private final UserService userService;

    // TODO: 내 구매 확인이 아니면 PURCHASE_CHECK_NOT_FOUND, 이미 답했으면 PURCHASE_CHECK_ALREADY_ANSWERED.
    //  구매 확인 생성은 하루 한 번 도는 스케줄러(ShedLock)가 맡는다
    //  클릭 후 7일이 지났고 아직 purchase_checks가 없는 product_link_clicks로 PurchaseCheck를 만들고 PURCHASE_CHECK 알림을 보낸다
    //  재실행/중복 실행은 uk_purchase_checks_product_link_click_id가 막는다
    @Transactional
    public PurchaseCheckResponse answerPurchaseCheck(Long userId, Long purchaseCheckId, boolean purchased) {
        userService.validateActiveUser(userId);
        throw new UnsupportedOperationException("미구현");
    }
}
