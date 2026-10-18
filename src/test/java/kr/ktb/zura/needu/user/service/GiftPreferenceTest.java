package kr.ktb.zura.needu.user.service;

import java.util.List;
import java.util.Map;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.user.type.AllergyType;
import kr.ktb.zura.needu.user.type.GiftExclusionType;
import kr.ktb.zura.needu.user.type.InterestCategoryType;
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
                .containsExactly(InterestCategoryType.FASHION, InterestCategoryType.BEAUTY);
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
                List.of("COOKING", "COOKING"), List.of("MILK", "MILK"), List.of("PERFUME", "PERFUME"));

        assertThat(preference.interestCategories()).containsExactly(InterestCategoryType.COOKING);
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
                List.of("FASHION", "BEAUTY", "TRAVEL", "GAME", "MUSIC", "PET"), List.of(), List.of()));
    }

    @Test
    void unknownInterestCategory_from_throwsInvalidInput() {
        assertInvalidInput(() -> GiftPreference.from(List.of("CAMPING"), List.of(), List.of()));
    }

    @Test
    void productCategoryCode_from_throwsInvalidInput() {
        // 관심사는 상품 분류(ProductCategory)와 다른 코드표를 쓴다
        assertInvalidInput(() -> GiftPreference.from(List.of("LIVING"), List.of(), List.of()));
    }

    @Test
    void unknownAllergy_from_throwsInvalidInput() {
        assertInvalidInput(() -> GiftPreference.from(List.of(), List.of("POLLEN"), List.of()));
    }

    @Test
    void unknownGiftExclusion_from_throwsInvalidInput() {
        assertInvalidInput(() -> GiftPreference.from(List.of(), List.of(), List.of("perfume")));
    }

    @Test
    void savedTastes_fromOnboardingTastes_readsCodeLists() {
        GiftPreference preference = GiftPreference.fromOnboardingTastes(Map.of(
                "interestCategoryCodes", List.of("BEAUTY", "HOME_INTERIOR"),
                "allergyCodes", List.of("NUTS"),
                "giftExclusionCodes", List.of()));

        assertThat(preference.interestCategoryCodes()).containsExactly("BEAUTY", "HOME_INTERIOR");
        assertThat(preference.allergyCodes()).containsExactly("NUTS");
        assertThat(preference.giftExclusionCodes()).isEmpty();
    }

    @Test
    void removedCodeSaved_fromOnboardingTastes_skipsOnlyRemovedCode() {
        GiftPreference preference = GiftPreference.fromOnboardingTastes(Map.of(
                "interestCategoryCodes", List.of("LIVING", "BEAUTY"),
                "allergyCodes", List.of("POLLEN", "NUTS"),
                "giftExclusionCodes", List.of("PERFUME")));

        assertThat(preference.interestCategoryCodes()).containsExactly("BEAUTY");
        assertThat(preference.allergyCodes()).containsExactly("NUTS");
        assertThat(preference.giftExclusionCodes()).containsExactly("PERFUME");
    }

    @Test
    void missingKeys_fromOnboardingTastes_returnsEmptyLists() {
        GiftPreference preference = GiftPreference.fromOnboardingTastes(Map.of("allergyCodes", "MILK"));

        assertThat(preference.interestCategoryCodes()).isEmpty();
        assertThat(preference.allergyCodes()).isEmpty();
        assertThat(preference.giftExclusionCodes()).isEmpty();
    }

    private static void assertInvalidInput(ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.COMMON_INVALID_INPUT);
    }
}
