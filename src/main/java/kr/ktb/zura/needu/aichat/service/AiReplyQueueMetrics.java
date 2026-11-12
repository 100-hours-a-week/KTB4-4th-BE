package kr.ktb.zura.needu.aichat.service;

import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TimeGauge;
import kr.ktb.zura.needu.aichat.repository.AiReplyStreamStatus;
import org.springframework.stereotype.Component;

// 대기열 값은 Redis 하나를 읽으므로 모든 인스턴스가 같은 값을 낸다 => 대시보드에서는 인스턴스 중 최댓값으로 본다
@Component
public class AiReplyQueueMetrics {

    private final Counter queueFull;
    private final Counter claimed;
    private final Counter redispatched;
    private final Counter returned;
    private final Counter gaveUp;
    private final Counter commitRejected;
    private volatile QueueStatus queueStatus = QueueStatus.UNKNOWN;

    public AiReplyQueueMetrics(MeterRegistry meterRegistry) {
        this.queueFull = Counter.builder("aichat.reply.queue.rejected").register(meterRegistry);
        this.claimed = Counter.builder("aichat.reply.jobs.claimed").register(meterRegistry);
        this.redispatched = Counter.builder("aichat.reply.jobs.redispatched").register(meterRegistry);
        this.returned = Counter.builder("aichat.reply.jobs.returned").register(meterRegistry);
        this.gaveUp = Counter.builder("aichat.reply.jobs.gave.up").register(meterRegistry);
        this.commitRejected = Counter.builder("aichat.reply.commits.rejected").register(meterRegistry);
        Gauge.builder("aichat.reply.queue.length", this, metrics -> metrics.queueStatus.length())
                .strongReference(true)
                .register(meterRegistry);
        Gauge.builder("aichat.reply.queue.pending", this, metrics -> metrics.queueStatus.pendingCount())
                .strongReference(true)
                .register(meterRegistry);
        TimeGauge.builder("aichat.reply.queue.oldest.wait", this, TimeUnit.MILLISECONDS,
                        metrics -> metrics.queueStatus.oldestWaitingMillis())
                .strongReference(true)
                .register(meterRegistry);
        Gauge.builder("aichat.reply.permits.used", this, metrics -> metrics.queueStatus.permitsInUse())
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
