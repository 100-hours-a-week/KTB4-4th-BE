package kr.ktb.zura.needu.product.service;

import java.math.BigDecimal;
import java.util.List;

import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.product.dto.request.PersonalProductSearchCondition;
import kr.ktb.zura.needu.product.dto.response.PersonalProductResponse;
import kr.ktb.zura.needu.product.dto.response.ProductCursorPageResponse;
import kr.ktb.zura.needu.product.repository.PersonalProductRepository;
import kr.ktb.zura.needu.product.repository.PersonalProductSummary;
import kr.ktb.zura.needu.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PersonalProductService {

    private final UserService userService;
    private final PersonalProductRepository personalProductRepository;
    private final ProductPriceRangeCacheService productPriceRangeCacheService;

    public ProductCursorPageResponse<PersonalProductResponse> findAllPersonalProducts(
            Long userId, PersonalProductSearchCondition condition) {
        validatePriceRange(condition);
        validateCategory(condition);
        userService.validateActiveUser(userId);

        int size = condition.size();
        // 다음 페이지 존재 여부를 추가 count 쿼리 없이 판단하기 위해 한 건을 더 조회한다.
        List<PersonalProductSummary> personalProducts = findPersonalProducts(userId, condition, Limit.of(size + 1));
        boolean hasNext = personalProducts.size() > size;
        List<PersonalProductSummary> pageItems = hasNext ? personalProducts.subList(0, size) : personalProducts;

        String nextCursor = hasNext ? PersonalProductCursor.from(pageItems.getLast()).encode() : null;
        return new ProductCursorPageResponse<>(
                pageItems.stream().map(PersonalProductResponse::from).toList(),
                productPriceRangeCacheService.findPersonalPriceRange(userId),
                nextCursor,
                hasNext
        );
    }

    private void validatePriceRange(PersonalProductSearchCondition condition) {
        if (condition.minPrice() > condition.maxPrice()) {
            throw new BusinessException(CommonErrorCode.COMMON_INVALID_INPUT);
        }
    }

    // TODO: 카테고리 코드를 정한 뒤 없는 코드는 COMMON_INVALID_INPUT, 있는 코드는 조회 조건에 넣을 것
    private void validateCategory(PersonalProductSearchCondition condition) {
        if (condition.category() != null) {
            throw new UnsupportedOperationException("PROD-1 미구현");
        }
    }

    private List<PersonalProductSummary> findPersonalProducts(
            Long userId, PersonalProductSearchCondition condition, Limit limit) {
        BigDecimal minPrice = BigDecimal.valueOf(condition.minPrice());
        BigDecimal maxPrice = BigDecimal.valueOf(condition.maxPrice());

        if (condition.cursor() == null) {
            return personalProductRepository.findAllByUserIdAndPriceRange(userId, minPrice, maxPrice, limit);
        }
        PersonalProductCursor decodedCursor = PersonalProductCursor.decode(condition.cursor());
        return personalProductRepository.findAllByUserIdAndPriceRangeAfterCursor(
                userId, minPrice, maxPrice, decodedCursor.score(), decodedCursor.id(), limit);
    }
}
