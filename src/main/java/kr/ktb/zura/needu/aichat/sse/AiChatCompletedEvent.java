package kr.ktb.zura.needu.aichat.sse;

import kr.ktb.zura.needu.aichat.entity.AiMessage;

// 메시지 전송 응답과 필드가 같지만 따로 둔다 => HTTP 응답을 바꿔도 FE가 받는 이벤트 형식이 함께 바뀌지 않게
public record AiChatCompletedEvent(
        Long userMessageId,
        Long messageId,
        String content,
        Integer progress,
        Boolean inputLocked
) {

    public static AiChatCompletedEvent from(AiMessage reply) {
        return new AiChatCompletedEvent(
                reply.getReplyToMessageId(), reply.getId(), reply.getContent(),
                reply.getProgress(), reply.getInputLocked());
    }
}
