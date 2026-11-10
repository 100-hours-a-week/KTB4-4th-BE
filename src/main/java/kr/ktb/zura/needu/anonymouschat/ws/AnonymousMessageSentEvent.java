package kr.ktb.zura.needu.anonymouschat.ws;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AnonymousMessageSentEvent(
        Long roomId,
        UUID clientMessageId,
        Long messageId,
        String content,
        OffsetDateTime sentAt
) {
}
