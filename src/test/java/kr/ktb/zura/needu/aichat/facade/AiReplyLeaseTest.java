package kr.ktb.zura.needu.aichat.facade;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiReplyLeaseTest {

    private static final Instant STARTED_AT = Instant.parse("2026-11-07T12:00:00Z");
    private static final Duration WITHIN = Duration.ofSeconds(20);

    @Test
    void justStarted_isConfirmedWithin_returnsTrue() {
        AiReplyLease lease = AiReplyLease.start(Clock.fixed(STARTED_AT, ZoneOffset.UTC));

        assertThat(lease.isManaged()).isTrue();
        assertThat(lease.isConfirmedWithin(WITHIN)).isTrue();
        assertThat(lease.isLost()).isFalse();
    }

    @Test
    void notConfirmedForLongerThanLimit_isConfirmedWithin_returnsFalse() {
        MutableClock clock = new MutableClock(STARTED_AT);
        AiReplyLease lease = AiReplyLease.start(clock);

        clock.advance(WITHIN.plusSeconds(1));

        assertThat(lease.isConfirmedWithin(WITHIN)).isFalse();
    }

    @Test
    void confirmedAgain_isConfirmedWithin_countsFromLastConfirmation() {
        MutableClock clock = new MutableClock(STARTED_AT);
        AiReplyLease lease = AiReplyLease.start(clock);
        clock.advance(WITHIN);
        lease.confirm();

        clock.advance(WITHIN);

        assertThat(lease.isConfirmedWithin(WITHIN)).isTrue();
    }

    @Test
    void unmanagedLease_isConfirmedWithin_alwaysReturnsTrue() {
        AiReplyLease lease = AiReplyLease.unmanaged();

        assertThat(lease.isManaged()).isFalse();
        assertThat(lease.isConfirmedWithin(Duration.ZERO)).isTrue();
    }

    @Test
    void jobTaken_attach_exposesTargetForHeartbeat() {
        AiReplyLease lease = AiReplyLease.start(Clock.fixed(STARTED_AT, ZoneOffset.UTC));

        lease.attach(101L, 201L, "lock-token");

        assertThat(lease.target()).contains(new AiReplyLease.Target(101L, 201L, "lock-token"));
    }

    @Test
    void threadBound_requestReturn_marksReturningAndInterruptsThread() throws InterruptedException {
        AiReplyLease lease = AiReplyLease.start(Clock.fixed(STARTED_AT, ZoneOffset.UTC));
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread thread = Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(Duration.ofMinutes(1));
            } catch (InterruptedException e) {
                interrupted.set(true);
            }
        });
        lease.bindThread(thread);

        lease.requestReturn();

        assertThat(thread.join(Duration.ofSeconds(5))).isTrue();
        assertThat(interrupted).isTrue();
        assertThat(lease.isReturning()).isTrue();
    }

    @Test
    void noThreadBound_requestReturn_marksReturning() {
        AiReplyLease lease = AiReplyLease.start(Clock.fixed(STARTED_AT, ZoneOffset.UTC));

        lease.requestReturn();

        assertThat(lease.isReturning()).isTrue();
        assertThat(lease.isLost()).isFalse();
    }

    static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
