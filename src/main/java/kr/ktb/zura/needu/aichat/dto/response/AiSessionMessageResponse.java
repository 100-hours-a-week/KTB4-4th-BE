package kr.ktb.zura.needu.aichat.dto.response;

import kr.ktb.zura.needu.aichat.client.dto.response.AiServerSessionMessageResponse;

import java.time.LocalDateTime;

public record AiSessionMessageResponse(String role, String content, LocalDateTime createdAt) {

    public static AiSessionMessageResponse from(AiServerSessionMessageResponse response) {
        return new AiSessionMessageResponse(response.role(), response.content(), response.createdAt());
    }
}
