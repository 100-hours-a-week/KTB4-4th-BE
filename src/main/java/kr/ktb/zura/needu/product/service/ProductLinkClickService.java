package kr.ktb.zura.needu.product.service;

import kr.ktb.zura.needu.product.dto.request.CreateProductLinkClickRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductLinkClickService {

    // TODO: 없는 상품이면 COMMON_INVALID_INPUT. 클릭을 기록하고 구매 확인의 대상으로 삼는다.
    @Transactional
    public void createLinkClick(Long userId, CreateProductLinkClickRequest request) {
        throw new UnsupportedOperationException("미구현");
    }
}
