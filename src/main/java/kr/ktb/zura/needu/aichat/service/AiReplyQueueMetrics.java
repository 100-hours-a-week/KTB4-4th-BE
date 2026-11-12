package kr.ktb.zura.needu.aichat.service;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.TimeGauge;
import kr.ktb.zura.needu.aichat.repository.AiReplyStreamStatus;
import kr.ktb.zura.needu.aichat.type.AiReplyProcessingResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AiReplyQueueMetrics {

    private final Counter queueFull;
    private final Counter claimed;
    private final Counter redispatched;
    private final Counter returned;
    private final Counter gaveUp;
    private final Counter commitRejected;
    private final Counter turnInProgressRetry;
    private final MeterRegistry meterRegistry;
    private final Map<AiReplyProcessingResult, Counter> processingResults =
            new EnumMap<>(AiReplyProcessingResult.class);
    private final Map<AiReplyProcessingResult, Timer> processingTimers =
            new EnumMap<>(AiReplyProcessingResult.class);
    private volatile QueueStatus queueStatus = QueueStatus.UNKNOWN;

    public AiReplyQueueMetrics(MeterRegistry meterRegistry,
                               @Value("${ai.chat.reply-queue.worker-enabled:false}") boolean workerEnabled) {
        this.meterRegistry = meterRegistry;
        this.queueFull = counter(meterRegistry, "aichat.reply.queue.rejected",
                "AI reply jobs rejected because the queue reached its limit");
        this.claimed = counter(meterRegistry, "aichat.reply.jobs.claimed",
                "Idle pending AI reply jobs claimed by another worker");
        this.redispatched = counter(meterRegistry, "aichat.reply.jobs.redispatched",
                "AI reply jobs restored to the stream by reconciliation");
        this.returned = counter(meterRegistry, "aichat.reply.jobs.returned",
                "AI reply jobs returned to the queue during worker shutdown");
        this.gaveUp = counter(meterRegistry, "aichat.reply.jobs.gave.up",
                "AI reply jobs abandoned after exceeding the delivery limit");
        this.commitRejected = counter(meterRegistry, "aichat.reply.commits.rejected",
                "AI reply commits rejected after ownership was lost");
        this.turnInProgressRetry = Counter.builder("aichat.reply.jobs.retries")
                .description("AI reply processing retries by bounded reason")
                .tag("reason", "turn_in_progress")
                .register(meterRegistry);
        for (AiReplyProcessingResult result : AiReplyProcessingResult.values()) {
            processingResults.put(result, Counter.builder("aichat.reply.jobs.processed")
                    .description("AI reply processing attempts by result")
                    .tag("result", result.tagValue())
                    .register(meterRegistry));
            processingTimers.put(result, Timer.builder("aichat.reply.jobs.processing")
                    .description("AI reply processing duration in seconds by result")
                    .tag("result", result.tagValue())
                    .register(meterRegistry));
        }
        if (workerEnabled) {
            registerQueueGauges(meterRegistry);
        }
    }

    private static Counter counter(MeterRegistry meterRegistry, String name, String description) {
        return Counter.builder(name).description(description).register(meterRegistry);
    }

    // Redis 전체 상태는 Worker만 수집한다. 여러 Worker 값은 대시보드에서 max로 집계한다.
    private void registerQueueGauges(MeterRegistry meterRegistry) {
        Gauge.builder("aichat.reply.queue.length", this, metrics -> metrics.queueStatus.length())
                .description("Total AI reply stream length in jobs")
                .strongReference(true)
                .register(meterRegistry);
        Gauge.builder("aichat.reply.queue.pending", this, metrics -> metrics.queueStatus.pendingCount())
                .description("AI reply consumer group pending count in jobs")
                .strongReference(true)
                .register(meterRegistry);
        TimeGauge.builder("aichat.reply.queue.oldest.wait", this, TimeUnit.MILLISECONDS,
                        metrics -> metrics.queueStatus.oldestWaitingMillis())
                .description("Age of the oldest pending AI reply job in seconds")
                .strongReference(true)
                .register(meterRegistry);
        Gauge.builder("aichat.reply.permits.used", this, metrics -> metrics.queueStatus.permitsInUse())
                .description("Global AI reply concurrency permits in use")
                .strongReference(true)
                .register(meterRegistry);
    }

    public void recordQueueFull() {
        queueFull.increment();
    }

    public void recordClaimed() {
        claimed.increment();
    }

    public void recordRedispatched() {
        redispatched.increment();
    }

    public void recordReturned() {
        returned.increment();
    }

    public void recordGaveUp() {
        gaveUp.increment();
    }

    public void recordCommitRejected() {
        commitRejected.increment();
    }

    public void recordTurnInProgressRetry() {
        turnInProgressRetry.increment();
    }

    public Timer.Sample startProcessing() {
        return Timer.start(meterRegistry);
    }

    // result는 세 가지 고정값만 사용하며 작업·사용자·대화 식별자는 label로 등록하지 않는다.
    public void recordProcessing(Timer.Sample sample, AiReplyProcessingResult result) {
        processingResults.get(result).increment();
        sample.stop(processingTimers.get(result));
    }

    public void updateQueueStatus(AiReplyStreamStatus streamStatus, long permitsInUse) {
        queueStatus = new QueueStatus(streamStatus.length(), streamStatus.pendingCount(),
                streamStatus.oldestWaiting().toMillis(), permitsInUse);
    }

    // 읽지 못한 값을 0이나 이전 값으로 두면 대기열이 비었거나 그대로인 것처럼 보인다
    public void clearQueueStatus() {
        queueStatus = QueueStatus.UNKNOWN;
    }

    private record QueueStatus(double length, double pendingCount, double oldestWaitingMillis, double permitsInUse) {

        private static final QueueStatus UNKNOWN = new QueueStatus(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
    }
}
