package kr.ktb.zura.needu.aichat.service;

import kr.ktb.zura.needu.aichat.entity.AiMessage;
import kr.ktb.zura.needu.common.cursor.CursorCodec;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;

// 정렬 기준(id desc)의 마지막 값을 불투명한 문자열로 전달해, 클라이언트가 커서 내부 구조에 의존하지 않도록 한다.
public record AiMessageCursor(Long messageId) {

    public static AiMessageCursor from(AiMessage aiMessage) {
        return new AiMessageCursor(aiMessage.getId());
    }

    public static AiMessageCursor decode(String cursor) {
        try {
            String decoded = CursorCodec.decode(cursor);
            long messageId = Long.parseLong(decoded);
            if (messageId <= 0) {
                throw invalidCursor();
            }
            return new AiMessageCursor(messageId);
        } catch (IllegalArgumentException e) {
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
