package kr.ktb.zura.needu.anonymouschat.dto.response;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import kr.ktb.zura.needu.anonymouschat.entity.AnonymousMessage;
import kr.ktb.zura.needu.anonymouschat.type.AnonymousMessageSender;

public record AnonymousMessageSummaryResponse(
        Long messageId,
        AnonymousMessageSender sender,
        String content,
        int unreadCount,
        OffsetDateTime sentAt
) {

    public static AnonymousMessageSummaryResponse from(
            AnonymousMessage message, Long viewerUserId, Long partnerLastReadMessageId) {
        AnonymousMessageSender sender = AnonymousMessageSender.of(message.getSenderUserId(), viewerUserId);
        boolean unreadByPartner = sender == AnonymousMessageSender.ME
                && (partnerLastReadMessageId == null || message.getId() > partnerLastReadMessageId);
        return new AnonymousMessageSummaryResponse(
                message.getId(),
                sender,
                message.getContent(),
                unreadByPartner ? 1 : 0,
                message.getCreatedAt().atOffset(ZoneOffset.UTC)
        );
    }
}
