package kr.ktb.zura.needu.aichat.client.dto.response;

import java.time.LocalDateTime;

public record AiServerSessionMessageResponse(String role, String content, LocalDateTime createdAt) {
}
