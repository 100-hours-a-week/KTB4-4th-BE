package kr.ktb.zura.needu.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import org.junit.jupiter.api.Test;

class NotificationCursorTest {

    @Test
    void validCursor_encodeAndDecode_preservesNotificationId() {
        NotificationCursor cursor = new NotificationCursor(501L);

        assertThat(cursor.encode()).isEqualTo("eyJpZCI6NTAxfQ==");
        assertThat(NotificationCursor.decode(cursor.encode()).id()).isEqualTo(501L);
    }

    @Test
    void malformedCursor_decode_throwsInvalidRequest() {
        assertThatThrownBy(() -> NotificationCursor.decode("invalid"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.COMMON_INVALID_REQUEST);
    }
}
