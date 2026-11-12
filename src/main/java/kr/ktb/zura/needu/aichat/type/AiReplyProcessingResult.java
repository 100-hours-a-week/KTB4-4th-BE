package kr.ktb.zura.needu.aichat.type;

public enum AiReplyProcessingResult {
    SUCCESS("success"),
    RETRYABLE_FAILURE("retryable_failure"),
    TERMINAL_FAILURE("terminal_failure");

    private final String tagValue;

    AiReplyProcessingResult(String tagValue) {
        this.tagValue = tagValue;
    }

    public String tagValue() {
        return tagValue;
    }
}
