package kr.ktb.zura.needu.common.cursor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;

public final class CursorCodec {

    private CursorCodec() {
    }

    public static String encode(String raw) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static String decode(String cursor) {
        try {
            return new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(CommonErrorCode.COMMON_INVALID_REQUEST);
        }
    }
}
