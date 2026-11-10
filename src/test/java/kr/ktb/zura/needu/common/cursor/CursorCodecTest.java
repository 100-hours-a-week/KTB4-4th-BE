package kr.ktb.zura.needu.common.cursor;

import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CursorCodecTest {

    @Test
    void unicodeValue_encodeAndDecode_restoresValueWithoutPadding() {
        String encoded = CursorCodec.encode("101:익명의 수달");

        assertThat(encoded).doesNotContain("=", "+", "/");
        assertThat(CursorCodec.decode(encoded)).isEqualTo("101:익명의 수달");
    }

    @Test
    void paddedCursor_decode_restoresValue() {
        assertThat(CursorCodec.decode("eyJpZCI6NTAxfQ==")).isEqualTo("{\"id\":501}");
    }

    @Test
    void malformedCursor_decode_throwsInvalidRequest() {
        assertThatThrownBy(() -> CursorCodec.decode("not-base64!!"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.COMMON_INVALID_REQUEST);
    }
}
