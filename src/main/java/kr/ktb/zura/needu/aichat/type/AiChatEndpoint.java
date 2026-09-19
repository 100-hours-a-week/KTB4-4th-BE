package kr.ktb.zura.needu.aichat.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;

@Getter
@RequiredArgsConstructor
public enum AiChatEndpoint {

    CHECK_HEALTH("/health", HttpMethod.GET),
    START_CHAT("/chats", HttpMethod.POST),
    SEND_MESSAGE("/chats/{chatId}/messages", HttpMethod.POST),
    CONFIRM_TASTE_PROFILE("/taste-profiles/{tasteProfileId}/confirm", HttpMethod.POST);

    private final String url;
    private final HttpMethod httpMethod;
}
