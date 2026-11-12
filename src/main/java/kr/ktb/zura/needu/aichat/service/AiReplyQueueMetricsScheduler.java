package kr.ktb.zura.needu.aichat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 지표를 내보낼 때마다 Redis를 부르지 않도록 주기마다 읽어 둔다
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.chat.reply-queue.worker-enabled", havingValue = "true")
public class AiReplyQueueMetricsScheduler {

    private final AiReplyQueueService aiReplyQueueService;

    @Scheduled(fixedDelayString = "${ai.chat.reply-queue.metrics-interval}")
    public void recordQueueStatus() {
        aiReplyQueueService.recordQueueStatus();
    }
}
