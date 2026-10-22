package kr.ktb.zura.needu.notification.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import kr.ktb.zura.needu.notification.type.NotificationType;
import org.springframework.stereotype.Component;

// 알림 생성 단계별 소요 시간을 알림 타입별 Timer에 누적한다.
// 배치는 실행 전후 snapshot 차이로 한 번의 실행 비용을 구하고, 같은 값을 Prometheus로도 볼 수 있다.
@Component
public class NotificationStageTimer {

    private static final String METRIC_NAME = "needu.notification.stage";

    private final MeterRegistry meterRegistry;

    public NotificationStageTimer(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public <T> T time(NotificationType type, NotificationStage stage, Supplier<T> task) {
        return timer(type, stage).record(task);
    }

    public void run(NotificationType type, NotificationStage stage, Runnable task) {
        timer(type, stage).record(task);
    }

    public Map<NotificationStage, Duration> snapshot(NotificationType type) {
        Map<NotificationStage, Duration> totals = new EnumMap<>(NotificationStage.class);
        for (NotificationStage stage : NotificationStage.values()) {
            totals.put(stage, Duration.ofNanos((long) timer(type, stage).totalTime(TimeUnit.NANOSECONDS)));
        }
        return totals;
    }

    private Timer timer(NotificationType type, NotificationStage stage) {
        return Timer.builder(METRIC_NAME)
                .tag("type", type.name())
                .tag("stage", stage.metricTag())
                .register(meterRegistry);
    }
}
