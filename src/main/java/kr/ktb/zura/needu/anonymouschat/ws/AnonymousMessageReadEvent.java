package kr.ktb.zura.needu.anonymouschat.ws;

import java.time.OffsetDateTime;

public record AnonymousMessageReadEvent(
        Long roomId,
        Long lastReadMessageId,
        OffsetDateTime readAt
) {
}
