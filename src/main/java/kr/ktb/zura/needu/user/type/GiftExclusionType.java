package kr.ktb.zura.needu.user.type;

import java.util.Arrays;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import lombok.Getter;

@Getter
public enum GiftExclusionType {
    PERFUME("향수"),
    ALCOHOL("주류"),
    CLOTHING("의류"),
    FOOD("식품"),
    COSMETICS("화장품"),
    VOUCHER("교환권");

    private final String displayName;

    GiftExclusionType(String displayName) {
        this.displayName = displayName;
    }

    public static GiftExclusionType fromCode(String code) {
        return Arrays.stream(values())
                .filter(type -> type.name().equals(code))
                .findFirst()
                .orElseThrow(() -> new BusinessException(CommonErrorCode.COMMON_INVALID_INPUT));
    }
}
