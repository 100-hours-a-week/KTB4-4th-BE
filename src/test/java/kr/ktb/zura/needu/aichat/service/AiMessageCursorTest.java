package kr.ktb.zura.needu.aichat.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiMessageCursorTest {

    @Test
    void encodedCursor_decode_restoresMessageId() {
        String cursor = new AiMessageCursor(101L).encode();

        assertThat(AiMessageCursor.decode(cursor).messageId()).isEqualTo(101L);
    }

    @Test
    void notBase64Cursor_decode_throwsInvalidRequestException() {
        assertThatInvalidRequest("not-base64!!");
    }

    @Test
    void nonNumericCursor_decode_throwsInvalidRequestException() {
        assertThatInvalidRequest(encode("abc"));
    }

    @Test
    void nonPositiveMessageId_decode_throwsInvalidRequestException() {
        assertThatInvalidRequest(encode("0"));
    }

    private static void assertThatInvalidRequest(String cursor) {
        assertThatThrownBy(() -> AiMessageCursor.decode(cursor))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.COMMON_INVALID_REQUEST);
    }

    private static String encode(String raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
