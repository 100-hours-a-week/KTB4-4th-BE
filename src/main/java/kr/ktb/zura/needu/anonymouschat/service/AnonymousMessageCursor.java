package kr.ktb.zura.needu.anonymouschat.service;

import kr.ktb.zura.needu.anonymouschat.entity.AnonymousMessage;
import kr.ktb.zura.needu.common.cursor.CursorCodec;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;

public record AnonymousMessageCursor(Long messageId) {

    public static AnonymousMessageCursor from(AnonymousMessage message) {
        return new AnonymousMessageCursor(message.getId());
    }

    public static AnonymousMessageCursor decode(String cursor) {
        try {
            long messageId = Long.parseLong(CursorCodec.decode(cursor));
            if (messageId <= 0) {
                throw invalidCursor();
            }
            return new AnonymousMessageCursor(messageId);
        } catch (IllegalArgumentException exception) {
            throw invalidCursor();
        }
    }

    public String encode() {
        return CursorCodec.encode(String.valueOf(messageId));
    }

    private static BusinessException invalidCursor() {
        return new BusinessException(CommonErrorCode.COMMON_INVALID_REQUEST);
    }
}
