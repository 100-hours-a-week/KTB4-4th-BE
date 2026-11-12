package kr.ktb.zura.needu.aichat.service;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import kr.ktb.zura.needu.aichat.repository.AiReplyStreamStatus;
import kr.ktb.zura.needu.aichat.type.AiReplyProcessingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiReplyQueueMetricsTest {

    private SimpleMeterRegistry meterRegistry;
    private AiReplyQueueMetrics aiReplyQueueMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        aiReplyQueueMetrics = new AiReplyQueueMetrics(meterRegistry, true);
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

    @Test
    void processingRecorded_recordsBoundedResultCounterAndTimer() {
        var sample = aiReplyQueueMetrics.startProcessing();

        aiReplyQueueMetrics.recordProcessing(sample, AiReplyProcessingResult.SUCCESS);

        assertThat(meterRegistry.get("aichat.reply.jobs.processed")
                .tag("result", "success").counter().count()).isEqualTo(1);
        assertThat(meterRegistry.get("aichat.reply.jobs.processing")
                .tag("result", "success").timer().count()).isEqualTo(1);
        assertThat(meterRegistry.get("aichat.reply.jobs.processed").meters())
                .extracting(meter -> meter.getId().getTag("result"))
                .containsExactlyInAnyOrder("success", "retryable_failure", "terminal_failure");
    }

    @Test
    void turnInProgressRetryRecorded_usesBoundedReasonLabel() {
        aiReplyQueueMetrics.recordTurnInProgressRetry();

        assertThat(meterRegistry.get("aichat.reply.jobs.retries")
                .tag("reason", "turn_in_progress").counter().count()).isEqualTo(1);
        assertThat(meterRegistry.getMeters().stream()
                .flatMap(meter -> meter.getId().getTags().stream())
                .map(tag -> tag.getKey() + "=" + tag.getValue()))
                .allMatch(Set.of(
                        "result=success",
                        "result=retryable_failure",
                        "result=terminal_failure",
                        "reason=turn_in_progress")::contains);
    }

    @Test
    void apiInstance_registersProducerCountersWithoutQueueGauges() {
        SimpleMeterRegistry apiRegistry = new SimpleMeterRegistry();

        new AiReplyQueueMetrics(apiRegistry, false).recordQueueFull();

        assertThat(apiRegistry.find("aichat.reply.queue.rejected").counter()).isNotNull();
        assertThat(apiRegistry.find("aichat.reply.queue.length").gauge()).isNull();
        assertThat(apiRegistry.find("aichat.reply.queue.pending").gauge()).isNull();
        assertThat(apiRegistry.find("aichat.reply.permits.used").gauge()).isNull();
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
