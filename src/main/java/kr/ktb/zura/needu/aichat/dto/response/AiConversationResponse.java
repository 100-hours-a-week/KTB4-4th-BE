package kr.ktb.zura.needu.aichat.dto.response;

import java.time.OffsetDateTime;

import kr.ktb.zura.needu.aichat.entity.AiChatRoom;
import kr.ktb.zura.needu.aichat.type.AiChatRoomStatus;

public record AiConversationResponse(
        Long conversationId,
        AiChatRoomStatus status,
        int progress,
        OffsetDateTime nextConversationAvailableAt
) {

    public AiConversationResponse(Long conversationId, AiChatRoomStatus status, int progress) {
        this(conversationId, status, progress, null);
    }

    public static AiConversationResponse from(AiChatRoom aiChatRoom, int progress) {
        return new AiConversationResponse(aiChatRoom.getId(), aiChatRoom.getStatus(), progress);
    }

    public static AiConversationResponse from(
            AiChatRoom aiChatRoom, int progress, OffsetDateTime nextConversationAvailableAt) {
        return new AiConversationResponse(
                aiChatRoom.getId(), aiChatRoom.getStatus(), progress, nextConversationAvailableAt);
    }
}
