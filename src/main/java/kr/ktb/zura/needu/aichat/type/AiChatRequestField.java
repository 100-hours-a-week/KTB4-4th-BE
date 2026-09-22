package kr.ktb.zura.needu.aichat.type;

public enum AiChatRequestField {
    CONVERSATION_ROOM_ID("conversationRoomId");

    private final String fieldName;

    AiChatRequestField(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return fieldName;
    }
}
