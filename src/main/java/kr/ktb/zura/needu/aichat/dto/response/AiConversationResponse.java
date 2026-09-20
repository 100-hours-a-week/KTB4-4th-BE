package kr.ktb.zura.needu.aichat.dto.response;

import kr.ktb.zura.needu.aichat.entity.AiChatRoom;
import kr.ktb.zura.needu.aichat.type.AiChatRoomStatus;

public record AiConversationResponse(Long conversationId, AiChatRoomStatus status) {

    public static AiConversationResponse from(AiChatRoom aiChatRoom) {
        return new AiConversationResponse(aiChatRoom.getId(), aiChatRoom.getStatus());
    }
}
