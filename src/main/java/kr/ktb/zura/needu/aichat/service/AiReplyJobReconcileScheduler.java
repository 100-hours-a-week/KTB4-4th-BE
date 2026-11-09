package kr.ktb.zura.needu.aichat.service;

import java.time.Duration;

import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "ai.chat.reply-queue.enabled", havingValue = "true")
public class AiReplyJobReconcileScheduler {

    private final AiReplyQueueService aiReplyQueueService;
    private final Duration staleAfter;

    public AiReplyJobReconcileScheduler(
            AiReplyQueueService aiReplyQueueService,
            @Value("${ai.chat.reply-queue.reconcile-stale-after}") Duration staleAfter
    ) {
        this.aiReplyQueueService = aiReplyQueueService;
        this.staleAfter = staleAfter;
    }

    @Scheduled(fixedDelayString = "${ai.chat.reply-queue.reconcile-interval}")
    @SchedulerLock(
            name = "aiReplyJobReconcile",
            lockAtMostFor = "${ai.chat.reply-queue.reconcile-lock-at-most-for}",
            lockAtLeastFor = "${ai.chat.reply-queue.reconcile-lock-at-least-for}"
    )
    public void redispatchLostJobs() {
        LockAssert.assertLocked();
        aiReplyQueueService.redispatchLostJobs(staleAfter);
    }
}
