package kr.ktb.zura.needu.product.type;

import java.util.Arrays;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import lombok.Getter;

// 추천 목록의 category 필터와 온보딩 관심 카테고리가 같은 코드 사용
@Getter
public enum ProductCategory {
    VOUCHER("교환권"),
    LIVING("리빙"),
    BEAUTY("뷰티"),
    FASHION("패션"),
    FOOD("식품"),
    DIGITAL("가전·디지털"),
    HEALTH("건강"),
    LUXURY("명품"),
    BOOKS_TICKETS("책·음반티켓"),
    SPORTS("레저·스포츠"),
    PET("반려동물");

    private final String displayName;

    ProductCategory(String displayName) {
        this.displayName = displayName;
    }

    public static ProductCategory fromCode(String code) {
        return Arrays.stream(values())
                .filter(category -> category.name().equals(code))
                .findFirst()
                .orElseThrow(() -> new BusinessException(CommonErrorCode.COMMON_INVALID_INPUT));
    }
}
