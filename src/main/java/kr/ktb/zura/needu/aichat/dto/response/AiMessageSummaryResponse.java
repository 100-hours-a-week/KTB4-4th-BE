package kr.ktb.zura.needu.aichat.dto.response;

import java.time.LocalDateTime;

import kr.ktb.zura.needu.aichat.entity.AiMessage;
import kr.ktb.zura.needu.aichat.type.SenderType;

public record AiMessageSummaryResponse(Long messageId, SenderType role, String content, LocalDateTime createdAt) {

    public static AiMessageSummaryResponse from(AiMessage aiMessage) {
        return new AiMessageSummaryResponse(
                aiMessage.getId(),
                aiMessage.getSenderType(),
                aiMessage.getContent(),
                aiMessage.getCreatedAt()
        );
    }
}
