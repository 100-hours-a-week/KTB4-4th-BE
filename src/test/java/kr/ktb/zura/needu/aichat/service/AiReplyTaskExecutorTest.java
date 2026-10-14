package kr.ktb.zura.needu.aichat.service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiReplyTaskExecutorTest {

    private static final long WAIT_SECONDS = 5;

    private AiReplyTaskExecutor executor;

    @AfterEach
    void tearDown() {
        if (executor != null && executor.isRunning()) {
            executor.stop();
        }
    }

    @Test
    void reservedSlot_execute_runsTaskOnVirtualThread() throws Exception {
        executor = startedExecutor(1, Duration.ofSeconds(1));
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Thread> taskThread = new AtomicReference<>();

        executor.tryReserve().orElseThrow().execute(() -> {
            taskThread.set(Thread.currentThread());
            finished.countDown();
        });

        assertThat(finished.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        assertThat(taskThread.get().isVirtual()).isTrue();
        assertThat(taskThread.get().getName()).startsWith("aichat-reply-");
    }

    @Test
    void allSlotsInUse_tryReserve_returnsEmpty() {
        executor = startedExecutor(1, Duration.ofSeconds(1));

        assertThat(executor.tryReserve()).isPresent();
        assertThat(executor.tryReserve()).isEmpty();
    }

    @Test
    void releasedSlot_tryReserve_reservesAgain() {
        executor = startedExecutor(1, Duration.ofSeconds(1));

        AiReplyTaskExecutor.Slot slot = executor.tryReserve().orElseThrow();
        slot.release();
        slot.release();

        assertThat(executor.tryReserve()).isPresent();
        assertThat(executor.tryReserve()).isEmpty();
    }

    @Test
    void finishedTask_tryReserve_reservesFreedSlot() throws Exception {
        executor = startedExecutor(1, Duration.ofSeconds(1));
        CountDownLatch finished = new CountDownLatch(1);

        executor.tryReserve().orElseThrow().execute(finished::countDown);

        assertThat(finished.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        assertThat(awaitReserve()).isPresent();
    }

    @Test
    void failedTask_tryReserve_reservesFreedSlot() {
        executor = startedExecutor(1, Duration.ofSeconds(1));

        executor.tryReserve().orElseThrow().execute(() -> {
            throw new IllegalStateException("reply failed");
        });

        assertThat(awaitReserve()).isPresent();
    }

    @Test
    void notStarted_tryReserve_returnsEmpty() {
        executor = new AiReplyTaskExecutor(1, Duration.ofSeconds(1));

        assertThat(executor.tryReserve()).isEmpty();
    }

    @Test
    void stopped_tryReserve_returnsEmpty() {
        executor = startedExecutor(1, Duration.ofSeconds(1));

        executor.stop();

        assertThat(executor.tryReserve()).isEmpty();
    }

    @Test
    void slotReservedBeforeStop_execute_throwsServiceUnavailableWithoutRunningTask() {
        executor = startedExecutor(1, Duration.ofSeconds(1));
        AiReplyTaskExecutor.Slot slot = executor.tryReserve().orElseThrow();
        AtomicBoolean ran = new AtomicBoolean();
        executor.stop();

        assertThatThrownBy(() -> slot.execute(() -> ran.set(true)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(CommonErrorCode.COMMON_SERVICE_UNAVAILABLE);
        assertThat(ran).isFalse();
    }

    @Test
    void runningTask_stop_waitsForTaskToFinish() throws Exception {
        executor = startedExecutor(1, Duration.ofSeconds(WAIT_SECONDS));
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean finished = new AtomicBoolean();
        executor.tryReserve().orElseThrow().execute(() -> {
            started.countDown();
            sleep(Duration.ofMillis(200));
            finished.set(true);
        });
        assertThat(started.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

        executor.stop();

        assertThat(finished).isTrue();
    }

    @Test
    void taskLongerThanShutdownTimeout_stop_interruptsTask() throws Exception {
        executor = startedExecutor(1, Duration.ofMillis(100));
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        executor.tryReserve().orElseThrow().execute(() -> {
            started.countDown();
            try {
                Thread.sleep(Duration.ofSeconds(WAIT_SECONDS * 2));
            } catch (InterruptedException e) {
                interrupted.countDown();
            }
        });
        assertThat(started.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

        executor.stop();

        assertThat(interrupted.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    private static AiReplyTaskExecutor startedExecutor(int maxConcurrency, Duration shutdownTimeout) {
        AiReplyTaskExecutor started = new AiReplyTaskExecutor(maxConcurrency, shutdownTimeout);
        started.start();
        return started;
    }

    // 자리는 작업 스레드의 finally에서 돌려주므로, 작업이 끝났다는 신호 직후에는 아직 비어 있지 않을 수 있다
    private Optional<AiReplyTaskExecutor.Slot> awaitReserve() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (System.nanoTime() < deadline) {
            Optional<AiReplyTaskExecutor.Slot> slot = executor.tryReserve();
            if (slot.isPresent()) {
                return slot;
            }
            Thread.onSpinWait();
        }
        return Optional.empty();
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
