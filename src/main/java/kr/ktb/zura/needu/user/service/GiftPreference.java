package kr.ktb.zura.needu.user.service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.user.type.AllergyType;
import kr.ktb.zura.needu.user.type.GiftExclusionType;
import kr.ktb.zura.needu.user.type.InterestCategoryType;

public record GiftPreference(
        List<InterestCategoryType> interestCategories,
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
                toTypes(interestCategoryCodes, InterestCategoryType::fromCode),
                toTypes(allergyCodes, AllergyType::fromCode),
                toTypes(giftExclusionCodes, GiftExclusionType::fromCode));
        preference.validate();
        return preference;
    }

    static GiftPreference fromOnboardingTastes(Map<String, Object> onboardingTastes) {
        return new GiftPreference(
                toStoredTypes(onboardingTastes.get(INTEREST_CATEGORY_CODES_KEY), InterestCategoryType.class),
                toStoredTypes(onboardingTastes.get(ALLERGY_CODES_KEY), AllergyType.class),
                toStoredTypes(onboardingTastes.get(GIFT_EXCLUSION_CODES_KEY), GiftExclusionType.class));
    }

    Map<String, Object> toOnboardingTastes() {
        return Map.of(
                INTEREST_CATEGORY_CODES_KEY, interestCategoryCodes(),
                ALLERGY_CODES_KEY, allergyCodes(),
                GIFT_EXCLUSION_CODES_KEY, giftExclusionCodes());
    }

    public List<String> interestCategoryCodes() {
        return toCodes(interestCategories);
    }

    public List<String> allergyCodes() {
        return toCodes(allergies);
    }

    public List<String> giftExclusionCodes() {
        return toCodes(giftExclusions);
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

    private static <E extends Enum<E>> List<E> toStoredTypes(Object codes, Class<E> type) {
        if (!(codes instanceof List<?> codeList)) {
            return List.of();
        }
        return codeList.stream()
                .map(String::valueOf)
                .flatMap(code -> Arrays.stream(type.getEnumConstants())
                        .filter(constant -> constant.name().equals(code)))
                .toList();
    }

    private static List<String> toCodes(List<? extends Enum<?>> types) {
        return types.stream().map(Enum::name).toList();
    }
}
