package kr.ktb.zura.needu.aichat.dto.response;

import kr.ktb.zura.needu.aichat.entity.AiChatRoom;
import kr.ktb.zura.needu.aichat.type.AiChatRoomStatus;

public record AiConversationResponse(Long conversationId, AiChatRoomStatus status, int progress) {

    public static AiConversationResponse from(AiChatRoom aiChatRoom, int progress) {
        return new AiConversationResponse(aiChatRoom.getId(), aiChatRoom.getStatus(), progress);
    }
}
