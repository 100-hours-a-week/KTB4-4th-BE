package kr.ktb.zura.needu.product.type;

import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductCategoryTest {

    @Test
    void existingCode_fromCode_returnsCategory() {
        assertThat(ProductCategory.fromCode("BOOKS_TICKETS")).isEqualTo(ProductCategory.BOOKS_TICKETS);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"UNKNOWN", "HOME_INTERIOR", "living", "리빙"})
    void unknownCode_fromCode_throwsInvalidInput(String code) {
        assertThatThrownBy(() -> ProductCategory.fromCode(code))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.COMMON_INVALID_INPUT);
    }
}
