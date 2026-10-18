package kr.ktb.zura.needu.aichat.service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AiReplyTaskExecutor implements SmartLifecycle {

    private static final int PHASE = SmartLifecycle.DEFAULT_PHASE - 4096;
    private static final Duration INTERRUPTED_TASK_GRACE = Duration.ofSeconds(2);

    private final int maxConcurrency;
    private final Semaphore permits;
    private final Duration shutdownTimeout;
    private final ExecutorService executor =
            Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("aichat-reply-", 0).factory());
    private volatile boolean running;

    public AiReplyTaskExecutor(
            @Value("${ai.chat.reply-max-concurrency}") int maxConcurrency,
            @Value("${ai.chat.reply-shutdown-timeout}") Duration shutdownTimeout
    ) {
        this.maxConcurrency = maxConcurrency;
        this.permits = new Semaphore(maxConcurrency);
        this.shutdownTimeout = shutdownTimeout;
    }

    public Optional<Slot> tryReserve() {
        if (!running || !permits.tryAcquire()) {
            return Optional.empty();
        }
        return Optional.of(new Slot());
    }

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        running = false;
        executor.shutdown();
        try {
            if (executor.awaitTermination(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                return;
            }
            log.warn("AI reply tasks interrupted on shutdown. remaining={}",
                    maxConcurrency - permits.availablePermits());
            executor.shutdownNow();
            executor.awaitTermination(INTERRUPTED_TASK_GRACE.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return PHASE;
    }

    public final class Slot {

        private final AtomicBoolean released = new AtomicBoolean();

        private Slot() {
        }

        public void execute(Runnable task) {
            try {
                executor.execute(() -> {
                    try {
                        task.run();
                    } finally {
                        release();
                    }
                });
            } catch (RejectedExecutionException e) {
                release();
                throw new BusinessException(CommonErrorCode.COMMON_SERVICE_UNAVAILABLE, null, e);
            }
        }

        public void release() {
            if (released.compareAndSet(false, true)) {
                permits.release();
            }
        }
    }
}
