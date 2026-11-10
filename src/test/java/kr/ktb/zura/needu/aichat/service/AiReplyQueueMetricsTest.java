package kr.ktb.zura.needu.aichat.service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import kr.ktb.zura.needu.aichat.repository.AiReplyStreamStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiReplyQueueMetricsTest {

    private SimpleMeterRegistry meterRegistry;
    private AiReplyQueueMetrics aiReplyQueueMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        aiReplyQueueMetrics = new AiReplyQueueMetrics(meterRegistry);
    }

    @Test
    void eventsRecorded_record_incrementsEachCounter() {
        aiReplyQueueMetrics.recordQueueFull();
        aiReplyQueueMetrics.recordClaimed();
        aiReplyQueueMetrics.recordClaimed();
        aiReplyQueueMetrics.recordRedispatched();
        aiReplyQueueMetrics.recordReturned();
        aiReplyQueueMetrics.recordGaveUp();
        aiReplyQueueMetrics.recordCommitRejected();

        assertThat(counter("aichat.reply.queue.rejected")).isEqualTo(1);
        assertThat(counter("aichat.reply.jobs.claimed")).isEqualTo(2);
        assertThat(counter("aichat.reply.jobs.redispatched")).isEqualTo(1);
        assertThat(counter("aichat.reply.jobs.returned")).isEqualTo(1);
        assertThat(counter("aichat.reply.jobs.gave.up")).isEqualTo(1);
        assertThat(counter("aichat.reply.commits.rejected")).isEqualTo(1);
    }

    @Test
    void notReadYet_gauges_reportUnknown() {
        assertThat(gauge("aichat.reply.queue.length")).isNaN();
        assertThat(gauge("aichat.reply.queue.pending")).isNaN();
        assertThat(oldestWaitSeconds()).isNaN();
        assertThat(gauge("aichat.reply.permits.used")).isNaN();
    }

    @Test
    void queueStatusRead_updateQueueStatus_reportsLatestValues() {
        aiReplyQueueMetrics.updateQueueStatus(new AiReplyStreamStatus(12, 3, Duration.ofMillis(4500)), 2);

        assertThat(gauge("aichat.reply.queue.length")).isEqualTo(12);
        assertThat(gauge("aichat.reply.queue.pending")).isEqualTo(3);
        assertThat(oldestWaitSeconds()).isEqualTo(4.5);
        assertThat(gauge("aichat.reply.permits.used")).isEqualTo(2);
    }

    @Test
    void readFailedAfterUpdate_clearQueueStatus_reportsUnknown() {
        aiReplyQueueMetrics.updateQueueStatus(new AiReplyStreamStatus(12, 3, Duration.ofSeconds(4)), 2);

        aiReplyQueueMetrics.clearQueueStatus();

        assertThat(gauge("aichat.reply.queue.length")).isNaN();
        assertThat(oldestWaitSeconds()).isNaN();
    }

    private double counter(String name) {
        return meterRegistry.get(name).counter().count();
    }

    private double gauge(String name) {
        return meterRegistry.get(name).gauge().value();
    }

    private double oldestWaitSeconds() {
        return meterRegistry.get("aichat.reply.queue.oldest.wait").timeGauge().value(TimeUnit.SECONDS);
    }
}
