package kr.ktb.zura.needu.product.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersonalProductCursorTest {

    @Test
    void encodedCursor_decode_returnsSameScoreAndId() {
        PersonalProductCursor cursor = new PersonalProductCursor(new BigDecimal("0.912345"), 5001L);

        PersonalProductCursor decoded = PersonalProductCursor.decode(cursor.encode());

        assertThat(decoded.score()).isEqualByComparingTo("0.912345");
        assertThat(decoded.id()).isEqualTo(5001L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-base64!!", "MC45MTIzNDU", "YWJjOjEw", "MC45OmFiYw", "MC45OjA", "MC45OjE6Mg"})
    void malformedCursor_decode_throwsInvalidRequest(String cursor) {
        assertThatThrownBy(() -> PersonalProductCursor.decode(cursor))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.COMMON_INVALID_REQUEST);
    }

    @Test
    void cursorEncoded_encode_returnsUrlSafeStringWithoutPadding() {
        String encoded = new PersonalProductCursor(new BigDecimal("0.5"), 1L).encode();

        assertThat(encoded).doesNotContain("=", "+", "/");
        assertThat(new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8)).isEqualTo("0.5:1");
    }
}
