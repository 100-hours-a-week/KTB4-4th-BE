package kr.ktb.zura.needu.product.type;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Locale;

public enum PlatformType {
    COUPANG,
    KAKAO,
    ELEVEN_STREET,
    HOMEPLUS;

    @JsonCreator
    public static PlatformType from(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
