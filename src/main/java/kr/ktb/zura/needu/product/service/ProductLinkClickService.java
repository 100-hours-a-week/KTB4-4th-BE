package kr.ktb.zura.needu.product.service;

import kr.ktb.zura.needu.product.dto.request.CreateProductLinkClickRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductLinkClickService {

    // TODO: 없는 상품이면 COMMON_INVALID_INPUT. 클릭을 기록하고 구매 확인의 대상으로 삼는다.
    //  최근 7일 안에 (userId, productId, context)가 같은 클릭이 있으면 새로 저장하지 않고 그대로 204
    //  행 하나가 구매 확인 하나가 되므로, 같은 상품을 반복 클릭해도 7일 안에는 구매 확인이 한 번만 만들어진다
    //  7일은 설정값으로 관리
    @Transactional
    public void createLinkClick(Long userId, CreateProductLinkClickRequest request) {
        throw new UnsupportedOperationException("미구현");
    }
}
