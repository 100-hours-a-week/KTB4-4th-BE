package kr.ktb.zura.needu.product.service;

import java.util.List;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.product.dto.response.ProductCategoryResponse;
import kr.ktb.zura.needu.product.dto.response.ProductDetailResponse;
import kr.ktb.zura.needu.product.type.ProductContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductService {

    // TODO: products.category 자유 문자열을 코드로 정리한 뒤 코드와 탭 이름을 내려주도록 구현
    //  전체 카테고리 조회 API
    public List<ProductCategoryResponse> findAllCategories() {
        throw new UnsupportedOperationException("전체 카테고리 조회 로직 미구현");
    }

    // TODO: context별 추천 목록에서 상품을 찾고 없으면 PRODUCT_RECOMMENDATION_NOT_FOUND.
    //  FRIEND_GIFT는 친구 여부(FRIEND_NOT_FOUND)와 취향 분석 여부(PRODUCT_GIFT_RECOMMENDATION_FORBIDDEN)를 확인할 것
    public ProductDetailResponse findProduct(Long userId, Long productId, ProductContext context, Long friendUserId) {
        validateFriendUserId(context, friendUserId);
        throw new UnsupportedOperationException("미구현");
    }

    private void validateFriendUserId(ProductContext context, Long friendUserId) {
        if (context == ProductContext.FRIEND_GIFT && friendUserId == null) {
            throw new BusinessException(CommonErrorCode.COMMON_INVALID_INPUT);
        }
    }
}
