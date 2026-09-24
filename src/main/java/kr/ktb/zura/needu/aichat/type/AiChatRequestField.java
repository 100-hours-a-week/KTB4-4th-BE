package kr.ktb.zura.needu.aichat.type;

import lombok.Getter;

@Getter
public enum AiChatRequestField {
    CONVERSATION_ROOM_ID("conversationRoomId"),
    USER_ID("userId");

    private final String fieldName;

    AiChatRequestField(String fieldName) {
        this.fieldName = fieldName;
    }

}
