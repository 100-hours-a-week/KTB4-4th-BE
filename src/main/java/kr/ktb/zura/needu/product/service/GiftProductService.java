package kr.ktb.zura.needu.product.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.friend.service.FriendService;
import kr.ktb.zura.needu.product.dto.request.GiftProductSearchCondition;
import kr.ktb.zura.needu.product.dto.response.GiftProductResponse;
import kr.ktb.zura.needu.product.dto.response.MyGiftProductResponse;
import kr.ktb.zura.needu.product.dto.response.ProductCursorPageResponse;
import kr.ktb.zura.needu.product.exception.ProductErrorCode;
import kr.ktb.zura.needu.product.repository.GiftProductRepository;
import kr.ktb.zura.needu.product.repository.GiftProductSummary;
import kr.ktb.zura.needu.user.dto.response.UserDetailResponse;
import kr.ktb.zura.needu.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GiftProductService {

    private final UserService userService;
    private final FriendService friendService;
    private final GiftProductRepository giftProductRepository;
    private final ProductPriceRangeCacheService productPriceRangeCacheService;

    public ProductCursorPageResponse<GiftProductResponse> findAllGiftProducts(
            Long userId, Long friendUserId, GiftProductSearchCondition condition) {
        validatePriceRange(condition);
        validateCategory(condition);
        userService.validateActiveUser(userId);
        validateTasteAnalysisCompleted(friendService.findFriendUser(userId, friendUserId));
        return findGiftProductPage(friendUserId, condition, GiftProductResponse::from);
    }

    public ProductCursorPageResponse<MyGiftProductResponse> findAllMyGiftProducts(
            Long userId, GiftProductSearchCondition condition) {
        validatePriceRange(condition);
        validateCategory(condition);
        userService.validateActiveUser(userId);
        return findGiftProductPage(userId, condition, MyGiftProductResponse::from);
    }

    private <T> ProductCursorPageResponse<T> findGiftProductPage(
            Long ownerUserId, GiftProductSearchCondition condition, Function<GiftProductSummary, T> mapper) {
        int size = condition.size();
        // 다음 페이지 존재 여부를 추가 count 쿼리 없이 판단하기 위해 한 건을 더 조회한다.
        List<GiftProductSummary> giftProducts = findGiftProducts(ownerUserId, condition, Limit.of(size + 1));
        boolean hasNext = giftProducts.size() > size;
        List<GiftProductSummary> pageItems = hasNext ? giftProducts.subList(0, size) : giftProducts;

        String nextCursor = hasNext ? GiftProductCursor.from(pageItems.getLast()).encode() : null;
        return new ProductCursorPageResponse<>(
                pageItems.stream().map(mapper).toList(),
                productPriceRangeCacheService.findGiftPriceRange(ownerUserId),
                nextCursor,
                hasNext
        );
    }

    private void validatePriceRange(GiftProductSearchCondition condition) {
        if (condition.minPrice() > condition.maxPrice()) {
            throw new BusinessException(CommonErrorCode.COMMON_INVALID_INPUT);
        }
    }

    // TODO: 카테고리 코드를 정한 뒤 없는 코드는 COMMON_INVALID_INPUT, 있는 코드는 조회 조건에 넣을 것
    private void validateCategory(GiftProductSearchCondition condition) {
        if (condition.category() != null) {
            throw new UnsupportedOperationException("PROD-1 미구현");
        }
    }

    // 추천 상품은 친구의 취향 분석 결과로 만들어지므로, 분석이 끝나지 않은 친구는 조회할 수 없다.
    private void validateTasteAnalysisCompleted(UserDetailResponse friend) {
        if (!friend.tasteAnalysisCompleted()) {
            throw new BusinessException(ProductErrorCode.PRODUCT_GIFT_RECOMMENDATION_FORBIDDEN);
        }
    }

    private List<GiftProductSummary> findGiftProducts(
            Long ownerUserId, GiftProductSearchCondition condition, Limit limit) {
        BigDecimal minPrice = BigDecimal.valueOf(condition.minPrice());
        BigDecimal maxPrice = BigDecimal.valueOf(condition.maxPrice());
        if (condition.cursor() == null) {
            return giftProductRepository.findAllByUserIdAndPriceRange(ownerUserId, minPrice, maxPrice, limit);
        }
        GiftProductCursor decodedCursor = GiftProductCursor.decode(condition.cursor());
        return giftProductRepository.findAllByUserIdAndPriceRangeAfterCursor(
                ownerUserId, minPrice, maxPrice, decodedCursor.score(), decodedCursor.id(), limit);
    }
}
