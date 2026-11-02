package kr.ktb.zura.needu.aichat.client.dto.request;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

public record AiServerStartSessionRequest(
        Long userId,
        Long conversationRoomId,
        AiServerUserProfileRequest userProfile,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Map<String, Object> memory
) {
}
