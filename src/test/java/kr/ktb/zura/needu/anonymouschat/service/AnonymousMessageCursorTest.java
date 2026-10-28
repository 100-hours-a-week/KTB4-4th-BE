package kr.ktb.zura.needu.anonymouschat.service;

import kr.ktb.zura.needu.common.cursor.CursorCodec;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnonymousMessageCursorTest {

    @Test
    void encodedCursor_decode_restoresMessageId() {
        AnonymousMessageCursor cursor = new AnonymousMessageCursor(101L);

        assertThat(AnonymousMessageCursor.decode(cursor.encode())).isEqualTo(cursor);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-1", ""})
    void nonPositiveOrNonNumericMessageId_decode_throwsInvalidRequest(String rawMessageId) {
        String cursor = CursorCodec.encode(rawMessageId);

        assertThatThrownBy(() -> AnonymousMessageCursor.decode(cursor))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.COMMON_INVALID_REQUEST);
    }

    @Test
    void notBase64_decode_throwsInvalidRequest() {
        assertThatThrownBy(() -> AnonymousMessageCursor.decode("not-base64!!"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.COMMON_INVALID_REQUEST);
    }
}
