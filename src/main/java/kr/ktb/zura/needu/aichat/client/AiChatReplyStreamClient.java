package kr.ktb.zura.needu.aichat.client;

import java.time.Duration;
import java.util.function.Consumer;

import kr.ktb.zura.needu.aichat.client.dto.response.AiServerSendMessageResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AiChatReplyStreamClient {

    private final AiChatClient aiChatClient;
    private final int chunkCodePoints;
    private final Duration chunkInterval;

    public AiChatReplyStreamClient(
            AiChatClient aiChatClient,
            @Value("${ai.server.reply-chunk-code-points}") int chunkCodePoints,
            @Value("${ai.server.reply-chunk-interval}") Duration chunkInterval
    ) {
        if (chunkCodePoints < 1) {
            throw new IllegalArgumentException("AI reply chunk code points must be positive");
        }
        if (chunkInterval.isNegative()) {
            throw new IllegalArgumentException("AI reply chunk interval must not be negative");
        }
        this.aiChatClient = aiChatClient;
        this.chunkCodePoints = chunkCodePoints;
        this.chunkInterval = chunkInterval;
    }

    public AiServerSendMessageResponse streamMessage(
            Long userId, Long conversationRoomId, String message, Consumer<String> onDelta) {
        AiServerSendMessageResponse response = aiChatClient.sendMessage(userId, conversationRoomId, message);
        if (response == null || response.reply() == null) {
            return response;
        }

        int[] codePoints = response.reply().codePoints().toArray();
        for (int start = 0; start < codePoints.length; start += chunkCodePoints) {
            if (start > 0) {
                pause();
            }
            int count = Math.min(chunkCodePoints, codePoints.length - start);
            onDelta.accept(new String(codePoints, start, count));
        }
        return response;
    }

    // 로컬에서 스트리밍처럼 보이게 하는 간격, 기본값 0이면 기다리지 않음
    private void pause() {
        if (chunkInterval.isZero()) {
            return;
        }
        try {
            Thread.sleep(chunkInterval);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI reply streaming was interrupted", exception);
        }
    }
}
