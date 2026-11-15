package kr.ktb.zura.needu.notification.service;

import java.util.Arrays;
import java.util.List;
import kr.ktb.zura.needu.notification.config.WebPushJobWorkerProperties;
import kr.ktb.zura.needu.notification.type.WebPushQueueType;
import org.springframework.context.SmartLifecycle;

// 대기열마다 워커를 하나씩 만들고 애플리케이션 기동·종료에 맞춰 함께 시작·정지한다
public class WebPushJobWorkerManager implements SmartLifecycle {

    private final List<WebPushJobWorker> workers;
    private volatile boolean running;

    public WebPushJobWorkerManager(
            WebPushJobService jobService,
            WebPushDeliveryService deliveryService,
            WebPushJobWorkerProperties properties
    ) {
        this.workers = Arrays.stream(WebPushQueueType.values())
                .map(queueType -> new WebPushJobWorker(
                        queueType,
                        jobService,
                        deliveryService,
                        properties.queue(queueType),
                        properties.shutdownTimeout()))
                .toList();
    }

    @Override
    public synchronized void start() {
        running = true;
        workers.forEach(WebPushJobWorker::start);
    }

    @Override
    public synchronized void stop() {
        running = false;
        workers.forEach(WebPushJobWorker::stop);
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
