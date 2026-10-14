package kr.ktb.zura.needu.aichat.sse;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AiChatEventType {
    CONNECTED("connected"),
    ACCEPTED("accepted"),
    DELTA("delta"),
    COMPLETED("completed"),
    ERROR("error"),
    DONE("done");

    private final String eventName;
}
