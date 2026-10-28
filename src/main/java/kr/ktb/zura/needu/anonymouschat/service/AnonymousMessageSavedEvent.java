package kr.ktb.zura.needu.anonymouschat.service;

import java.time.LocalDateTime;
import java.util.UUID;
import kr.ktb.zura.needu.anonymouschat.entity.AnonymousMessage;
import kr.ktb.zura.needu.anonymouschat.entity.AnonymousRoom;

public record AnonymousMessageSavedEvent(
        Long roomId,
        Long senderUserId,
        Long partnerUserId,
        Long messageId,
        UUID clientMessageId,
        String content,
        LocalDateTime sentAt,
        boolean duplicated
) {

    public static AnonymousMessageSavedEvent of(
            AnonymousRoom room, AnonymousMessage message, boolean duplicated) {
        return new AnonymousMessageSavedEvent(
                room.getId(),
                message.getSenderUserId(),
                room.getPartnerUserId(message.getSenderUserId()),
                message.getId(),
                message.getClientMessageId(),
                message.getContent(),
                message.getCreatedAt(),
                duplicated
        );
    }
}
