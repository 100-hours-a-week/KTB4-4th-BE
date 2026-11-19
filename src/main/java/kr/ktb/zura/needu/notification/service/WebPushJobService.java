package kr.ktb.zura.needu.notification.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import kr.ktb.zura.needu.notification.repository.WebPushJobInsertRow;
import kr.ktb.zura.needu.notification.repository.WebPushJobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebPushJobService {

    private final WebPushJobRepository webPushJobRepository;
    private final boolean enabled;
    private final int chunkSize;

    public WebPushJobService(
            WebPushJobRepository webPushJobRepository,
            @Value("${notification.webpush.enabled:false}") boolean webPushEnabled,
            @Value("${needu.notification.webpush-job.enabled}") boolean jobEnabled,
            @Value("${needu.notification.webpush-job.chunk-size}") int chunkSize
    ) {
        // 0 이하면 chunk 반복이 끝나지 않으므로 기동 시점에 막는다
        if (chunkSize <= 0) {
            throw new IllegalArgumentException(
                    "needu.notification.webpush-job.chunk-size must be positive. value=" + chunkSize);
        }
        this.webPushJobRepository = webPushJobRepository;
        this.enabled = webPushEnabled && jobEnabled;
        this.chunkSize = chunkSize;
    }

    /**
     * 알림 이벤트별 수신자를 chunk로 나눠 웹푸시 발송 작업을 만든다.
     * 알림을 저장한 트랜잭션 안에서만 호출해, 커밋된 알림만 빠짐없이 발송 대상이 되게 한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void createJobs(List<WebPushJobTarget> targets, LocalDateTime createdAt) {
        if (!enabled) {
            return;
        }

        List<WebPushJobInsertRow> rows = new ArrayList<>();
        for (WebPushJobTarget target : targets) {
            List<Long> notificationIds = target.notificationIds();
            for (int fromIndex = 0; fromIndex < notificationIds.size(); fromIndex += chunkSize) {
                int toIndex = Math.min(fromIndex + chunkSize, notificationIds.size());
                rows.add(new WebPushJobInsertRow(
                        target.notificationEventId(),
                        target.type().getWebPushQueueType(),
                        notificationIds.subList(fromIndex, toIndex)
                ));
            }
        }
        webPushJobRepository.insertAll(rows, createdAt);
    }
}
