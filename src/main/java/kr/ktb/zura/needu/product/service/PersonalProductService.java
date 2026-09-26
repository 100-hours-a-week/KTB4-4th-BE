package kr.ktb.zura.needu.product.service;

import java.math.BigDecimal;
import java.util.List;

import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.common.response.CursorPageResponse;
import kr.ktb.zura.needu.product.dto.request.PersonalProductSearchCondition;
import kr.ktb.zura.needu.product.dto.response.PersonalProductResponse;
import kr.ktb.zura.needu.product.entity.PersonalProduct;
import kr.ktb.zura.needu.product.repository.PersonalProductRepository;
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

    public CursorPageResponse<PersonalProductResponse> findAllPersonalProducts(
            Long userId, PersonalProductSearchCondition condition) {
        validatePriceRange(condition);
        userService.validateActiveUser(userId);

        int size = condition.size();
        // 다음 페이지 존재 여부를 추가 count 쿼리 없이 판단하기 위해 한 건을 더 조회한다.
        List<PersonalProduct> personalProducts = findPersonalProducts(userId, condition, Limit.of(size + 1));
        boolean hasNext = personalProducts.size() > size;
        List<PersonalProduct> pageItems = hasNext ? personalProducts.subList(0, size) : personalProducts;

        String nextCursor = hasNext ? PersonalProductCursor.from(pageItems.getLast()).encode() : null;
        return new CursorPageResponse<>(
                pageItems.stream().map(PersonalProductResponse::from).toList(),
                nextCursor,
                hasNext
        );
    }

    private void validatePriceRange(PersonalProductSearchCondition condition) {
        if (condition.minPrice() > condition.maxPrice()) {
            throw new BusinessException(CommonErrorCode.COMMON_INVALID_INPUT);
        }
    }

    private List<PersonalProduct> findPersonalProducts(
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
