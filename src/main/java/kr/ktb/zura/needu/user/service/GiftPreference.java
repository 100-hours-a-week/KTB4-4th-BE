package kr.ktb.zura.needu.user.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.product.type.ProductCategory;
import kr.ktb.zura.needu.user.type.AllergyType;
import kr.ktb.zura.needu.user.type.GiftExclusionType;

record GiftPreference(
        List<ProductCategory> interestCategories,
        List<AllergyType> allergies,
        List<GiftExclusionType> giftExclusions
) {

    private static final int MAX_INTEREST_CATEGORY_COUNT = 5;
    private static final String INTEREST_CATEGORY_CODES_KEY = "interestCategoryCodes";
    private static final String ALLERGY_CODES_KEY = "allergyCodes";
    private static final String GIFT_EXCLUSION_CODES_KEY = "giftExclusionCodes";

    static GiftPreference from(
            List<String> interestCategoryCodes, List<String> allergyCodes, List<String> giftExclusionCodes) {
        GiftPreference preference = new GiftPreference(
                toTypes(interestCategoryCodes, ProductCategory::fromCode),
                toTypes(allergyCodes, AllergyType::fromCode),
                toTypes(giftExclusionCodes, GiftExclusionType::fromCode));
        preference.validate();
        return preference;
    }

    Map<String, Object> toOnboardingTastes() {
        return Map.of(
                INTEREST_CATEGORY_CODES_KEY, toCodes(interestCategories),
                ALLERGY_CODES_KEY, toCodes(allergies),
                GIFT_EXCLUSION_CODES_KEY, toCodes(giftExclusions));
    }

    private void validate() {
        if (interestCategories.size() > MAX_INTEREST_CATEGORY_COUNT) {
            throw new BusinessException(CommonErrorCode.COMMON_INVALID_INPUT);
        }
    }

    private static <T> List<T> toTypes(List<String> codes, Function<String, T> fromCode) {
        if (codes == null) {
            return List.of();
        }
        return codes.stream().distinct().map(fromCode).toList();
    }

    private static List<String> toCodes(List<? extends Enum<?>> types) {
        return types.stream().map(Enum::name).toList();
    }
}
