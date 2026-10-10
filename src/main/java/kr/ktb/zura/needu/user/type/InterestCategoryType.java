package kr.ktb.zura.needu.user.type;

import java.util.Arrays;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import lombok.Getter;

@Getter
public enum InterestCategoryType {

    FASHION("패션"),
    BEAUTY("뷰티"),
    HOME_INTERIOR("홈·인테리어"),
    COFFEE_TEA("커피·차"),
    COOKING("요리·베이킹"),
    GOURMET("맛집 탐방"),
    TRAVEL("여행"),
    FITNESS("운동·헬스"),
    OUTDOOR("캠핑·아웃도어"),
    GAME("게임"),
    MUSIC("음악"),
    MOVIE_DRAMA("영화·드라마"),
    READING("독서"),
    TECH("IT·전자기기"),
    PET("반려동물");

    private final String displayName;

    InterestCategoryType(String displayName) {
        this.displayName = displayName;
    }

    public static InterestCategoryType fromCode(String code) {
        return Arrays.stream(values())
                .filter(type -> type.name().equals(code))
                .findFirst()
                .orElseThrow(() -> new BusinessException(CommonErrorCode.COMMON_INVALID_INPUT));
    }
}
