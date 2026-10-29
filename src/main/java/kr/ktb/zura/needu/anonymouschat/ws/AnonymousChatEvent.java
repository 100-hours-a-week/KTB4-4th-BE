package kr.ktb.zura.needu.anonymouschat.ws;

import kr.ktb.zura.needu.anonymouschat.type.AnonymousChatEventType;

public record AnonymousChatEvent(
        AnonymousChatEventType type,
        Object data
) {

    public static AnonymousChatEvent messageSent(AnonymousMessageSentEvent data) {
        return new AnonymousChatEvent(AnonymousChatEventType.ANONYMOUS_CHAT_MESSAGE_SENT, data);
    }

    public static AnonymousChatEvent messageReceived(AnonymousMessageReceivedEvent data) {
        return new AnonymousChatEvent(AnonymousChatEventType.ANONYMOUS_CHAT_MESSAGE_RECEIVED, data);
    }

    public static AnonymousChatEvent messageRead(AnonymousMessageReadEvent data) {
        return new AnonymousChatEvent(AnonymousChatEventType.ANONYMOUS_CHAT_MESSAGE_READ, data);
    }
}
