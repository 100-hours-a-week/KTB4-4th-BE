package kr.ktb.zura.needu.product.service;

import kr.ktb.zura.needu.product.dto.response.ProductFeedbackResponse;
import kr.ktb.zura.needu.product.type.ProductContext;
import kr.ktb.zura.needu.product.type.ProductFeedbackType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductFeedbackService {

    // TODO: 내 추천 목록(context)에 없는 상품이면 PRODUCT_RECOMMENDATION_NOT_FOUND.
    //  (사용자, 상품, context) 기준으로 행이 없으면 만들고 있으면 바꾸도록(upsert). feedback이 null이면 선택 취소
    @Transactional
    public ProductFeedbackResponse updateFeedback(
            Long userId, Long productId, ProductContext context, ProductFeedbackType feedback) {
        throw new UnsupportedOperationException("미구현");
    }
}
