package kr.ktb.zura.needu.user.type;

import java.util.Arrays;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import lombok.Getter;

@Getter
public enum AllergyType {

    PEANUT("땅콩"),
    NUTS("견과류"),
    MILK("우유"),
    EGG("달걀"),
    WHEAT("밀"),
    SOY("대두"),
    SHELLFISH("갑각류"),
    FISH("생선");

    private final String displayName;

    AllergyType(String displayName) {
        this.displayName = displayName;
    }

    public static AllergyType fromCode(String code) {
        return Arrays.stream(values())
                .filter(type -> type.name().equals(code))
                .findFirst()
                .orElseThrow(() -> new BusinessException(CommonErrorCode.COMMON_INVALID_INPUT));
    }
}
