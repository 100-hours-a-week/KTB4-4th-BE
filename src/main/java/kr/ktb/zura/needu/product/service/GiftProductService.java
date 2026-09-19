package kr.ktb.zura.needu.product.service;

import java.math.BigDecimal;
import java.util.List;

import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.common.response.CursorPageResponse;
import kr.ktb.zura.needu.friend.dto.response.FriendResponse;
import kr.ktb.zura.needu.friend.service.FriendService;
import kr.ktb.zura.needu.product.dto.request.GiftProductSearchCondition;
import kr.ktb.zura.needu.product.dto.response.GiftProductResponse;
import kr.ktb.zura.needu.product.entity.GiftProduct;
import kr.ktb.zura.needu.product.exception.ProductErrorCode;
import kr.ktb.zura.needu.product.repository.GiftProductRepository;
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

    public CursorPageResponse<GiftProductResponse> findAllGiftProducts(
            Long userId, Long friendUserId, GiftProductSearchCondition condition) {
        validatePriceRange(condition);
        userService.findUserSummary(userId);
        validateTasteAnalysisCompleted(friendService.findFriend(userId, friendUserId));

        int size = condition.size();
        // 다음 페이지 존재 여부를 추가 count 쿼리 없이 판단하기 위해 한 건을 더 조회한다.
        List<GiftProduct> giftProducts = findGiftProducts(friendUserId, condition, Limit.of(size + 1));
        boolean hasNext = giftProducts.size() > size;
        List<GiftProduct> pageItems = hasNext ? giftProducts.subList(0, size) : giftProducts;

        String nextCursor = hasNext ? GiftProductCursor.from(pageItems.getLast()).encode() : null;
        return new CursorPageResponse<>(
                pageItems.stream().map(GiftProductResponse::from).toList(),
                nextCursor,
                hasNext
        );
    }

    private void validatePriceRange(GiftProductSearchCondition condition) {
        if (condition.minPrice() > condition.maxPrice()) {
            throw new BusinessException(CommonErrorCode.COMMON_INVALID_INPUT);
        }
    }

    // 추천 상품은 친구의 취향 분석 결과로 만들어지므로, 분석이 끝나지 않은 친구는 조회할 수 없다.
    private void validateTasteAnalysisCompleted(FriendResponse friend) {
        if (!friend.tasteAnalysisCompleted()) {
            throw new BusinessException(ProductErrorCode.PRODUCT_GIFT_RECOMMENDATION_FORBIDDEN);
        }
    }

    private List<GiftProduct> findGiftProducts(Long friendUserId, GiftProductSearchCondition condition, Limit limit) {
        BigDecimal minPrice = BigDecimal.valueOf(condition.minPrice());
        BigDecimal maxPrice = BigDecimal.valueOf(condition.maxPrice());
        if (condition.cursor() == null) {
            return giftProductRepository.findAllByUserIdAndPriceRange(friendUserId, minPrice, maxPrice, limit);
        }
        GiftProductCursor decodedCursor = GiftProductCursor.decode(condition.cursor());
        return giftProductRepository.findAllByUserIdAndPriceRangeAfterCursor(
                friendUserId, minPrice, maxPrice, decodedCursor.score(), decodedCursor.id(), limit);
    }
}
