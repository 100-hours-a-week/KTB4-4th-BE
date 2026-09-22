package kr.ktb.zura.needu.aichat.type;

public enum AiChatResponseMessage {
    CONVERSATION_FOUND("AI 대화를 조회했습니다."),
    CONVERSATION_CREATED("AI 대화를 시작했습니다."),
    MESSAGE_SENT("메시지를 전송했습니다."),
    MESSAGES_FOUND("대화 메시지를 조회했습니다."),
    ANALYSIS_COMPLETED("취향 분석이 완료되었습니다.");

    private final String message;

    AiChatResponseMessage(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
