package kr.ktb.zura.needu.anonymouschat.service;

import java.time.LocalDateTime;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnonymousRoomCursorTest {

    @Test
    void encodedCursor_decode_restoresActivityAtAndRoomId() {
        AnonymousRoomCursor cursor = new AnonymousRoomCursor(
                LocalDateTime.of(2026, 10, 24, 15, 30, 0, 123_456_000), 3L);

        assertThat(AnonymousRoomCursor.decode(cursor.encode())).isEqualTo(cursor);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-base64!!", "MjAyNi0xMC0yNFQxNTozMHw", "YWJjfDM", "MjAyNi0xMC0yNFQxNTozMHww"})
    void malformedCursor_decode_throwsInvalidRequest(String cursor) {
        assertThatThrownBy(() -> AnonymousRoomCursor.decode(cursor))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.COMMON_INVALID_REQUEST);
    }
}
