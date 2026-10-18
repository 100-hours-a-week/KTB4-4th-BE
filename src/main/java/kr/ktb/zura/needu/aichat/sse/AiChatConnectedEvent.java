package kr.ktb.zura.needu.aichat.sse;

import kr.ktb.zura.needu.aichat.type.AiChatRoomStatus;

public record AiChatConnectedEvent(
        Long conversationId,
        AiChatRoomStatus status,
        boolean inputLocked,
        boolean generating,
        Long pendingUserMessageId
) {
}
