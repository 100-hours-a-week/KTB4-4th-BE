package kr.ktb.zura.needu.anonymouschat.ws;

import java.time.OffsetDateTime;
import kr.ktb.zura.needu.anonymouschat.type.AnonymousMessageSender;

public record AnonymousMessageReceivedEvent(
        Long roomId,
        Long messageId,
        AnonymousMessageSender sender,
        String content,
        OffsetDateTime sentAt
) {
}
