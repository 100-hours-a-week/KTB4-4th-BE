package kr.ktb.zura.needu.aichat.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;

@Getter
@RequiredArgsConstructor
public enum AiChatEndpoint {

    CHECK_HEALTH("/health", HttpMethod.GET),
    START_SESSION("/v1/chat/sessions", HttpMethod.POST),
    SEND_MESSAGE("/v1/chat/sessions/{conversationRoomId}/messages", HttpMethod.POST),
    CREATE_ANALYSIS("/v1/chat/sessions/{conversationRoomId}/analysis", HttpMethod.POST),
    PATCH_ANALYZE("/v1/chat/sessions/{conversationRoomId}/analysis", HttpMethod.PATCH),
    CONFIRM_ANALYSIS("/v1/chat/sessions/{conversationRoomId}/close", HttpMethod.POST);

    private final String url;
    private final HttpMethod httpMethod;
}
