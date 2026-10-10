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
import kr.ktb.zura.needu.product.type.ProductCategory;
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
        ProductCategory category = toCategory(condition.category());
        userService.validateActiveUser(userId);
        validateTasteAnalysisCompleted(friendService.findFriendUser(userId, friendUserId));
        return findGiftProductPage(friendUserId, condition, category, GiftProductResponse::from);
    }

    public ProductCursorPageResponse<MyGiftProductResponse> findAllMyGiftProducts(
            Long userId, GiftProductSearchCondition condition) {
        validatePriceRange(condition);
        ProductCategory category = toCategory(condition.category());
        userService.validateActiveUser(userId);

        return findGiftProductPage(userId, condition, category, MyGiftProductResponse::from);
    }

    private <T> ProductCursorPageResponse<T> findGiftProductPage(
            Long ownerUserId, GiftProductSearchCondition condition, ProductCategory category,
            Function<GiftProductSummary, T> mapper) {
        int size = condition.size();
        // 다음 페이지 존재 여부를 추가 count 쿼리 없이 판단하기 위해 한 건을 더 조회한다.
        List<GiftProductSummary> giftProducts =
                findGiftProducts(ownerUserId, condition, category, Limit.of(size + 1));
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

    private ProductCategory toCategory(String category) {
        return category == null ? null : ProductCategory.fromCode(category);
    }

    // 추천 상품은 친구의 취향 분석 결과로 만들어지므로, 분석이 끝나지 않은 친구는 조회할 수 없다.
    private void validateTasteAnalysisCompleted(UserDetailResponse friend) {
        if (!friend.tasteAnalysisCompleted()) {
            throw new BusinessException(ProductErrorCode.PRODUCT_GIFT_RECOMMENDATION_FORBIDDEN);
        }
    }

    private List<GiftProductSummary> findGiftProducts(
            Long ownerUserId, GiftProductSearchCondition condition, ProductCategory category, Limit limit) {
        BigDecimal minPrice = BigDecimal.valueOf(condition.minPrice());
        BigDecimal maxPrice = BigDecimal.valueOf(condition.maxPrice());
        if (condition.cursor() == null) {
            return giftProductRepository.findAllByUserIdAndPriceRange(
                    ownerUserId, minPrice, maxPrice, category, limit);
        }
        GiftProductCursor decodedCursor = GiftProductCursor.decode(condition.cursor());
        return giftProductRepository.findAllByUserIdAndPriceRangeAfterCursor(
                ownerUserId, minPrice, maxPrice, category, decodedCursor.score(), decodedCursor.id(), limit);
    }
}
