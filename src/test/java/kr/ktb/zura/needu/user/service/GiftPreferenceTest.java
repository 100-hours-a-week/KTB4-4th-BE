package kr.ktb.zura.needu.user.service;

import java.util.List;
import java.util.Map;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.product.type.ProductCategory;
import kr.ktb.zura.needu.user.type.AllergyType;
import kr.ktb.zura.needu.user.type.GiftExclusionType;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GiftPreferenceTest {

    @Test
    void validCodes_from_convertsToTypesAndTastesMap() {
        GiftPreference preference = GiftPreference.from(
                List.of("FASHION", "BEAUTY"), List.of("PEANUT"), List.of("PERFUME", "ALCOHOL"));

        assertThat(preference.interestCategories())
                .containsExactly(ProductCategory.FASHION, ProductCategory.BEAUTY);
        assertThat(preference.allergies()).containsExactly(AllergyType.PEANUT);
        assertThat(preference.giftExclusions())
                .containsExactly(GiftExclusionType.PERFUME, GiftExclusionType.ALCOHOL);
        assertThat(preference.toOnboardingTastes()).isEqualTo(Map.of(
                "interestCategoryCodes", List.of("FASHION", "BEAUTY"),
                "allergyCodes", List.of("PEANUT"),
                "giftExclusionCodes", List.of("PERFUME", "ALCOHOL")));
    }

    @Test
    void nullCodes_from_returnsEmptyLists() {
        GiftPreference preference = GiftPreference.from(null, null, null);

        assertThat(preference.interestCategories()).isEmpty();
        assertThat(preference.allergies()).isEmpty();
        assertThat(preference.giftExclusions()).isEmpty();
    }

    @Test
    void duplicatedCodes_from_keepsOnlyOnce() {
        GiftPreference preference = GiftPreference.from(
                List.of("FOOD", "FOOD"), List.of("MILK", "MILK"), List.of("PERFUME", "PERFUME"));

        assertThat(preference.interestCategories()).containsExactly(ProductCategory.FOOD);
        assertThat(preference.allergies()).containsExactly(AllergyType.MILK);
        assertThat(preference.giftExclusions()).containsExactly(GiftExclusionType.PERFUME);
    }

    @Test
    void noneCode_from_throwsInvalidInput() {
        // 화면에 "없음" 선택지가 없어 빈 배열이 곧 "없음"이다
        assertInvalidInput(() -> GiftPreference.from(List.of(), List.of(), List.of("NONE")));
    }

    @Test
    void moreThanFiveInterestCategories_from_throwsInvalidInput() {
        assertInvalidInput(() -> GiftPreference.from(
                List.of("VOUCHER", "LIVING", "BEAUTY", "FASHION", "FOOD", "DIGITAL"), List.of(), List.of()));
    }

    @Test
    void unknownInterestCategory_from_throwsInvalidInput() {
        assertInvalidInput(() -> GiftPreference.from(List.of("HOME_INTERIOR"), List.of(), List.of()));
    }

    @Test
    void unknownAllergy_from_throwsInvalidInput() {
        assertInvalidInput(() -> GiftPreference.from(List.of(), List.of("POLLEN"), List.of()));
    }

    @Test
    void unknownGiftExclusion_from_throwsInvalidInput() {
        assertInvalidInput(() -> GiftPreference.from(List.of(), List.of(), List.of("perfume")));
    }

    private static void assertInvalidInput(ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.COMMON_INVALID_INPUT);
    }
}
