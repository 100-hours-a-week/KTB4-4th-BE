package kr.ktb.zura.needu.notification.service;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import kr.ktb.zura.needu.notification.config.WebPushJobWorkerProperties;
import kr.ktb.zura.needu.notification.entity.WebPushJob;
import kr.ktb.zura.needu.notification.type.WebPushQueueType;
import lombok.extern.slf4j.Slf4j;

// 대기열 하나의 웹푸시 발송 작업을 가져와 처리한다. 대기열마다 인스턴스를 따로 둔다
@Slf4j
final class WebPushJobWorker {

    private final WebPushQueueType queueType;
    private final WebPushJobService jobService;
    private final WebPushDeliveryService deliveryService;
    private final WebPushJobWorkerProperties.QueueProperties properties;
    private final Duration shutdownTimeout;

    // 동시에 처리하는 작업 수와 동시에 보내는 웹푸시 요청 수를 따로 제한한다
    private final Semaphore jobSlots;
    private final Semaphore sendPermits;
    private final ExecutorService jobExecutor;
    private final ExecutorService sendExecutor;
    private volatile boolean running;
    private Thread poller;

    WebPushJobWorker(
            WebPushQueueType queueType,
            WebPushJobService jobService,
            WebPushDeliveryService deliveryService,
            WebPushJobWorkerProperties.QueueProperties properties,
            Duration shutdownTimeout
    ) {
        this.queueType = queueType;
        this.jobService = jobService;
        this.deliveryService = deliveryService;
        this.properties = properties;
        this.shutdownTimeout = shutdownTimeout;
        this.jobSlots = new Semaphore(properties.concurrency());
        this.sendPermits = new Semaphore(properties.sendConcurrency());
        String threadPrefix = "webpush-" + queueType.name().toLowerCase(Locale.ROOT);
        this.jobExecutor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name(threadPrefix + "-job-", 0).factory());
        this.sendExecutor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name(threadPrefix + "-send-", 0).factory());
    }

    synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        poller = Thread.ofVirtual()
                .name("webpush-" + queueType.name().toLowerCase(Locale.ROOT) + "-poller")
                .start(this::pollJobs);
        log.info("Web push job worker started. queueType={}", queueType);
    }

    synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        poller.interrupt();
        jobExecutor.shutdown();
        try {
            poller.join(shutdownTimeout.toMillis());
            if (!jobExecutor.awaitTermination(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                log.warn("Web push jobs interrupted on shutdown. queueType={}, remaining={}",
                        queueType, properties.concurrency() - jobSlots.availablePermits());
                jobExecutor.shutdownNow();
                sendExecutor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            jobExecutor.shutdownNow();
            sendExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        sendExecutor.shutdown();
        log.info("Web push job worker stopped. queueType={}", queueType);
    }

    boolean isRunning() {
        return running;
    }

    private void pollJobs() {
        while (running) {
            int reserved = 0;
            try {
                reserved = reserveSlots();
                List<WebPushJob> jobs = jobService.takeJobs(queueType, reserved);
                jobSlots.release(reserved - jobs.size());
                reserved = 0;
                jobs.forEach(this::dispatch);
                if (jobs.isEmpty()) {
                    Thread.sleep(properties.pollInterval());
                }
            } catch (InterruptedException exception) {
                jobSlots.release(reserved);
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException exception) {
                jobSlots.release(reserved);
                if (!running) {
                    return;
                }
                log.warn("Web push job take failed. queueType={}", queueType, exception);
                if (!sleepQuietly()) {
                    return;
                }
            }
        }
    }

    // 빈 슬롯이 하나 생길 때까지 기다린 뒤, 비어 있는 만큼(최대 max-take-count) 더 확보한다
    private int reserveSlots() throws InterruptedException {
        jobSlots.acquire();
        int reserved = 1;
        while (reserved < properties.maxTakeCount() && jobSlots.tryAcquire()) {
            reserved++;
        }
        return reserved;
    }

    private void dispatch(WebPushJob job) {
        try {
            jobExecutor.execute(() -> {
                try {
                    processJob(job);
                } finally {
                    jobSlots.release();
                }
            });
        } catch (RejectedExecutionException exception) {
            jobSlots.release();
        }
    }

    // 발송에 실패한 구독은 WebPushDeliveryService가 로그만 남긴다. 작업 처리 자체가 실패하면 리스가 만료될 때까지 그대로 둔다
    void processJob(WebPushJob job) {
        try {
            deliveryService.deliver(jobService.findDeliveryNotifications(job), this::executeSend);
            jobService.completeJob(job);
        } catch (RuntimeException exception) {
            log.warn("Web push job processing failed. Leaving job until lease expires. queueType={}, jobId={}",
                    queueType, job.getId(), exception);
        }
    }

    // 발송 허용 수를 넘으면 작업 스레드가 기다린다. 넘기지 못하면 예외로 알려 작업을 완료 처리하지 않게 한다
    private void executeSend(Runnable send) {
        try {
            sendPermits.acquire();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RejectedExecutionException("Web push send interrupted. queueType=" + queueType, exception);
        }
        try {
            sendExecutor.execute(() -> {
                try {
                    send.run();
                } finally {
                    sendPermits.release();
                }
            });
        } catch (RejectedExecutionException exception) {
            sendPermits.release();
            throw exception;
        }
    }

    private boolean sleepQuietly() {
        try {
            Thread.sleep(properties.pollInterval());
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
