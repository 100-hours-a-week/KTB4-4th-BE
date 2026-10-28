package kr.ktb.zura.needu.anonymouschat.ws;

import kr.ktb.zura.needu.anonymouschat.type.AnonymousChatEventType;
import kr.ktb.zura.needu.common.exception.ErrorCode;

public record AnonymousChatErrorEvent(
        AnonymousChatEventType type,
        String code,
        String message,
        AnonymousMessageFailedEvent data
) {

    public static AnonymousChatErrorEvent of(ErrorCode errorCode, AnonymousMessageFailedEvent data) {
        return new AnonymousChatErrorEvent(
                AnonymousChatEventType.ANONYMOUS_CHAT_ERROR,
                errorCode.name(),
                errorCode.getMessage(),
                data
        );
    }
}
